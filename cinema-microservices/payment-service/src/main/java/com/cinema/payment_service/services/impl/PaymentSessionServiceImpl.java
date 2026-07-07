package com.cinema.payment_service.services.impl;

import com.cinema.Enum.SuccessMessage;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.payment_service.config.MomoGatewayProperties;
import com.cinema.payment_service.dto.request.CinemaRevenueField;
import com.cinema.payment_service.dto.request.CinemaRevenueReportRequest;
import com.cinema.payment_service.dto.request.CreatePaymentSessionRequest;
import com.cinema.payment_service.dto.request.FilmRevenueField;
import com.cinema.payment_service.dto.request.FilmRevenueReportRequest;
import com.cinema.payment_service.dto.request.PaymentSessionField;
import com.cinema.payment_service.dto.request.PromotionPreviewRequest;
import com.cinema.payment_service.dto.request.RefundPaymentRequest;
import com.cinema.payment_service.dto.momo.MomoIpnRequest;
import com.cinema.payment_service.dto.response.CinemaRevenueItemResponse;
import com.cinema.payment_service.dto.response.CinemaRevenueReportResponse;
import com.cinema.payment_service.dto.response.CinemaRevenueSummaryResponse;
import com.cinema.payment_service.dto.response.FilmRevenueItemResponse;
import com.cinema.payment_service.dto.response.FilmRevenueReportResponse;
import com.cinema.payment_service.dto.response.FilmRevenueSummaryResponse;
import com.cinema.payment_service.dto.response.PaymentSessionResponse;
import com.cinema.payment_service.dto.response.PromotionSelectionResponse;
import com.cinema.payment_service.dto.response.PromotionPreviewResponse;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.request.DateRange;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.SortField;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.excel.ExcelExportUtils;
import com.cinema.payment_service.entity.PaymentTransaction;
import com.cinema.payment_service.entity.Promotion;
import com.cinema.payment_service.entity.PaymentTransactionPromotion;
import com.cinema.payment_service.enums.PaymentTransactionStatus;
import com.cinema.payment_service.grpc.CinemaGrpcClient;
import com.cinema.payment_service.grpc.BookingGrpcClient;
import com.cinema.payment_service.grpc.FilmGrpcClient;
import com.cinema.payment_service.grpc.UserGrpcClient;
import com.cinema.payment_service.mapper.PaymentMapper;
import com.cinema.payment_service.repository.PaymentTransactionRepository;
import com.cinema.payment_service.repository.PaymentTransactionRepositoryImpl;
import com.cinema.payment_service.repository.PaymentTransactionPromotionRepository;
import com.cinema.payment_service.repository.PromotionRepository;
import com.cinema.payment_service.services.PaymentCustomerRankSettlementOutboxService;
import com.cinema.payment_service.services.PaymentSessionService;
import com.cinema.payment_service.services.PaymentLoyaltyOutboxService;
import com.cinema.payment_service.support.MomoPaymentGatewayClient;
import com.cinema.payment_service.support.PromotionEngine;
import com.cinema.payment_service.support.PromotionQuote;
import com.cinema.payment_service.support.RevenueReportSupport;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
import com.cinema.text.SearchTextUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentSessionServiceImpl implements PaymentSessionService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(0, RoundingMode.HALF_UP);
    private static final BigDecimal LOYALTY_POINT_VALUE = BigDecimal.valueOf(1000L);

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentTransactionRepositoryImpl paymentTransactionRepositoryImpl;
    private final BookingGrpcClient bookingGrpcClient;
    private final CinemaGrpcClient cinemaGrpcClient;
    private final FilmGrpcClient filmGrpcClient;
    private final UserGrpcClient userGrpcClient;
    private final PaymentTransactionPromotionRepository paymentTransactionPromotionRepository;
    private final PromotionRepository promotionRepository;
    private final MomoGatewayProperties momoGatewayProperties;
    private final MomoPaymentGatewayClient momoPaymentGatewayClient;
    private final PromotionEngine promotionEngine;
    private final RevenueReportSupport revenueReportSupport;
    private final PaymentMapper paymentMapper;
    private final ObjectMapper objectMapper;
    private final PaymentLoyaltyOutboxService paymentLoyaltyOutboxService;
    private final PaymentCustomerRankSettlementOutboxService paymentCustomerRankSettlementOutboxService;

    @Override
    @Transactional
    public ActionMessageResponse createSession(CreatePaymentSessionRequest request, UUID requesterUserId,
            String requesterRole) {
        if (request == null || request.getBookingId() == null || requesterUserId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        UUID bookingId = request.getBookingId();
        String requestedPromotionCode = normalizePromotionCode(request.getPromotionCode());
        UUID requestedPromotionId = request.getPromotionId();
        long requestedLoyaltyPointsUsed = normalizeRequestedLoyaltyPoints(request.getLoyaltyPointsUsed());
        BookingGrpcClient.BookingPaymentContext bookingContext = bookingGrpcClient.getBookingPaymentContext(bookingId);
        ensureRequesterCanCreateSessionForBooking(requesterUserId, requesterRole, bookingContext);
        ensureBookable(bookingContext);

        if ("PAID".equalsIgnoreCase(bookingContext.paymentStatus())
                || "CONFIRMED".equalsIgnoreCase(bookingContext.bookingStatus())) {
            paymentTransactionRepository.findFirstByBookingIdAndStatusOrderByTimeCreatedDesc(
                    bookingId,
                    PaymentTransactionStatus.PAID)
                    .orElseGet(() -> paymentTransactionRepository.findFirstByBookingIdOrderByTimeCreatedDesc(bookingId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)));
        }

        PaymentTransaction latest = paymentTransactionRepository.findFirstByBookingIdOrderByTimeCreatedDesc(bookingId)
                .orElse(null);
        if (latest != null) {
            if (requiresTransactionOwnershipCheck(requesterRole)) {
                ensureRequesterOwnsTransaction(latest, requesterUserId);
            }
            if (isReusable(latest, bookingContext)
                    && canReuseLatestTransaction(
                    latest,
                    requestedPromotionId,
                    requestedPromotionCode,
                    requestedLoyaltyPointsUsed)) {
                validateRequestedLoyaltyPoints(bookingContext.userId(), requestedLoyaltyPointsUsed);
                syncBookingPromotionSnapshot(bookingContext, latest, null);
                return ActionMessageResponse.builder()
                        .message(SuccessMessage.PAYMENT_SESSION_CREATED.getMessage())
                        .build();
            }
        }

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setBookingId(bookingContext.bookingId());
        transaction.setShowtimeId(bookingContext.showtimeId());
        transaction.setCinemaId(bookingContext.cinemaId());
        transaction.setFilmId(bookingContext.filmId());
        transaction.setUserId(bookingContext.userId());
        transaction.setAmount(normalizeAmount(bookingContext.finalAmount()));
        transaction.setTicketSubtotalSnapshot(normalizeAmount(bookingContext.ticketSubtotal()));
        transaction.setProductSubtotalSnapshot(normalizeAmount(bookingContext.productSubtotal()));
        transaction.setCurrency("VND");
        transaction.setPaymentMethod("MOMO_QR");
        transaction.setOrderInvoiceNumber(buildInvoiceNumber(bookingContext.bookingId()));
        transaction.setStatus(PaymentTransactionStatus.PENDING);
        transaction.setExpiresAt(bookingContext.reservedUntil());

        List<PromotionQuote> appliedPromotions = applyPromotionIfNeeded(
                transaction,
                requestedPromotionId,
                requestedPromotionCode,
                bookingContext,
                requesterUserId);
        applyLoyaltyPointsIfNeeded(transaction, requestedLoyaltyPointsUsed);
        syncBookingPromotionSnapshot(bookingContext, transaction, appliedPromotions);

        transaction.setPayUrl(momoGatewayProperties.getRedirectUrl());
        PaymentTransaction persistedTransaction = paymentTransactionRepository.saveAndFlush(transaction);
        log.info(
                "MOMO_CREATE_PERSISTED transactionId={} bookingId={} orderId={} requestId={} status={} expiresAt={} amount={}",
                persistedTransaction.getId(),
                persistedTransaction.getBookingId(),
                persistedTransaction.getOrderInvoiceNumber(),
                persistedTransaction.getOrderInvoiceNumber(),
                persistedTransaction.getStatus(),
                persistedTransaction.getExpiresAt(),
                persistedTransaction.getAmount());
        log.info(
                "MOMO_CREATE_REQUEST transactionId={} bookingId={} orderId={} requestId={} redirectUrl={} ipnUrl={} amount={}",
                persistedTransaction.getId(),
                persistedTransaction.getBookingId(),
                persistedTransaction.getOrderInvoiceNumber(),
                persistedTransaction.getOrderInvoiceNumber(),
                momoGatewayProperties.getRedirectUrl(),
                momoGatewayProperties.getIpnUrl(),
                persistedTransaction.getAmount());

        MomoPaymentGatewayClient.MomoCheckoutResult checkoutResult = momoPaymentGatewayClient.createCheckout(
                persistedTransaction,
                bookingContext);
        persistedTransaction.setPayUrl(checkoutResult.payUrl());
        persistedTransaction.setCheckoutPayloadJson(checkoutResult.requestPayloadJson());
        persistedTransaction.setResponsePayloadJson(checkoutResult.responsePayloadJson());
        String responseResultCode = extractCheckoutResponseField(checkoutResult.responsePayloadJson(), "resultCode");
        String responseMessage = extractCheckoutResponseField(checkoutResult.responsePayloadJson(), "message");
        log.info(
                "MOMO_CREATE_RESPONSE transactionId={} bookingId={} orderId={} requestId={} resultCode={} message={} payUrl={} qrCodeUrl={}",
                persistedTransaction.getId(),
                persistedTransaction.getBookingId(),
                persistedTransaction.getOrderInvoiceNumber(),
                persistedTransaction.getOrderInvoiceNumber(),
                responseResultCode,
                responseMessage,
                checkoutResult.payUrl(),
                checkoutResult.qrCodeUrl());
        PaymentTransaction savedTransaction = paymentTransactionRepository.save(persistedTransaction);
        log.info(
                "MOMO_CREATE_SAVED transactionId={} bookingId={} orderId={} requestId={} status={} expiresAt={}",
                savedTransaction.getId(),
                savedTransaction.getBookingId(),
                savedTransaction.getOrderInvoiceNumber(),
                savedTransaction.getOrderInvoiceNumber(),
                savedTransaction.getStatus(),
                savedTransaction.getExpiresAt());
        savePromotionSnapshots(savedTransaction, appliedPromotions);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PAYMENT_SESSION_CREATED.getMessage())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentSessionResponse getSession(UUID bookingId, UUID requesterUserId, String requesterRole) {
        if (bookingId == null || requesterUserId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        PaymentTransaction transaction = paymentTransactionRepository
                .findFirstByBookingIdOrderByTimeCreatedDesc(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        ensureRequesterCanViewSession(transaction, requesterUserId, requesterRole);
        return toPaymentSessionResponse(transaction);
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public ActionMessageResponse completeSession(UUID bookingId, UUID requesterUserId, String requesterRole) {
        if (bookingId == null || requesterUserId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        PaymentTransaction transaction = paymentTransactionRepository
                .findFirstByBookingIdOrderByTimeCreatedDesc(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        ensureRequesterCanCompleteSession(transaction, requesterUserId, requesterRole);

        if (transaction.getStatus() == PaymentTransactionStatus.PAID) {
            enqueueLoyaltySync(transaction, "completeSession:alreadyPaid");
            enqueuePaymentSettlement(transaction, "completeSession:alreadyPaid");
            return ActionMessageResponse.builder()
                    .message(SuccessMessage.PAYMENT_SESSION_COMPLETED.getMessage())
                    .build();
        }
        if (transaction.getStatus() != PaymentTransactionStatus.PENDING) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        ensurePromotionUsageAvailable(transaction);

        String normalizedRole = normalizeRole(requesterRole);
        transaction.setStatus(PaymentTransactionStatus.PAID);
        transaction.setPaidAt(LocalDateTime.now());
        transaction.setProviderRef(null);
        transaction.setFailureReason(null);
        transaction.setCompletedByUserId(requesterUserId);
        transaction.setCompletedByRole(normalizedRole);
        paymentTransactionRepository.save(transaction);
        log.info(
                "PAYMENT_COMPLETE_MARK_PAID transactionId={} bookingId={} requesterUserId={} requesterRole={} amount={}",
                transaction.getId(),
                transaction.getBookingId(),
                requesterUserId,
                normalizedRole,
                transaction.getAmount());

        try {
            bookingGrpcClient.confirmBookingPayment(
                    transaction.getBookingId(),
                    transaction.getAmount(),
                    transaction.getPaymentMethod(),
                    transaction.getProviderRef(),
                    transaction.getOrderInvoiceNumber(),
                    transaction.getLoyaltyPointsUsed(),
                    transaction.getLoyaltyPointsEarned());
        } catch (BusinessException ex) {
            log.warn(
                    "PAYMENT_COMPLETE_CONFIRM_BOOKING_FAILED transactionId={} bookingId={} requesterUserId={} requesterRole={} errorCode={}",
                    transaction.getId(),
                    transaction.getBookingId(),
                    requesterUserId,
                    normalizedRole,
                    ex.getErrorCode().name());
            transaction.setFailureReason("BOOKING_CONFIRM_FAILED:" + ex.getErrorCode().name());
            paymentTransactionRepository.save(transaction);
            throw new BusinessException(ErrorCode.BOOKING_SERVICE_ERROR);
        }

        enqueueLoyaltySync(transaction, "completeSession");
        enqueuePaymentSettlement(transaction, "completeSession");

        log.info(
                "PAYMENT_COMPLETE_CONFIRMED transactionId={} bookingId={} requesterUserId={} requesterRole={} status={}",
                transaction.getId(),
                transaction.getBookingId(),
                requesterUserId,
                normalizedRole,
                transaction.getStatus());
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PAYMENT_SESSION_COMPLETED.getMessage())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentSessionResponse> searchMySessions(
            PageRequest<PaymentSessionField> request,
            UUID requesterUserId) {
        if (requesterUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        int page = request.getPageOrDefault();
        int size = request.getSizeOrDefault();
        String keyword = request.getNormalizedKeyword();

        List<SortField<PaymentSessionField>> sortFields = request.getSortBy();
        sortFields = sortFields == null ? new ArrayList<>() : new ArrayList<>(sortFields);
        if (sortFields.stream()
                .noneMatch(sort -> sort != null && sort.getField() == PaymentSessionField.TIME_CREATED)) {
            sortFields.add(new SortField<>(PaymentSessionField.TIME_CREATED, "DESC"));
        }

        long totalElements = paymentTransactionRepositoryImpl.countWithFilter(
                requesterUserId, keyword, request.getFilterBy());
        List<PaymentTransaction> sessions = paymentTransactionRepositoryImpl.searchWithPageAndSortAndFilter(
                requesterUserId, keyword, page, size, sortFields, request.getFilterBy());

        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

        return PageResponse.<PaymentSessionResponse>builder()
                .data(sessions.stream()
                        .map(transaction -> paymentMapper.toPaymentSessionResponse(
                                transaction,
                                readCheckoutFields(transaction.getCheckoutPayloadJson()),
                                extractCheckoutResponseField(transaction.getResponsePayloadJson(), "qrCodeUrl")))
                        .toList())
                .currentPage(page)
                .totalPages(totalPages)
                .totalElements(totalElements)
                .size(size)
                .hasNext(page < totalPages)
                .hasPrevious(page > 1)
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse requestRefund(UUID bookingId, UUID requesterUserId, RefundPaymentRequest request) {
        if (bookingId == null || requesterUserId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        PaymentTransaction transaction = paymentTransactionRepository
                .findFirstByBookingIdOrderByTimeCreatedDesc(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        ensureRequesterOwnsTransaction(transaction, requesterUserId);

        if (transaction.getStatus() == PaymentTransactionStatus.REFUND_PENDING
                || transaction.getStatus() == PaymentTransactionStatus.REFUNDED) {
            return ActionMessageResponse.builder()
                    .message(SuccessMessage.PAYMENT_REFUND_REQUESTED.getMessage())
                    .build();
        }
        if (transaction.getStatus() != PaymentTransactionStatus.PAID) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        BigDecimal refundAmount = normalizeAmount(
                request != null && request.getRefundAmount() != null ? request.getRefundAmount()
                        : transaction.getAmount());
        if (refundAmount.compareTo(ZERO) <= 0 || refundAmount.compareTo(normalizeAmount(transaction.getAmount())) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        transaction.setStatus(PaymentTransactionStatus.REFUND_PENDING);
        transaction.setRefundAmount(refundAmount);
        transaction.setRefundReason(request == null || !StringUtils.hasText(request.getReason())
                ? "CUSTOMER_REQUEST"
                : request.getReason().trim());
        transaction.setFailureReason(null);
        paymentTransactionRepository.save(transaction);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PAYMENT_REFUND_REQUESTED.getMessage())
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse completeRefund(UUID bookingId) {
        if (bookingId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        PaymentTransaction transaction = paymentTransactionRepository
                .findFirstByBookingIdOrderByTimeCreatedDesc(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (transaction.getStatus() == PaymentTransactionStatus.REFUNDED) {
            enqueueRefundSettlement(transaction, "completeRefund:alreadyRefunded");
            return ActionMessageResponse.builder()
                    .message(SuccessMessage.PAYMENT_REFUND_COMPLETED.getMessage())
                    .build();
        }
        if (transaction.getStatus() != PaymentTransactionStatus.REFUND_PENDING) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        BigDecimal refundAmount = normalizeAmount(
                transaction.getRefundAmount() == null ? transaction.getAmount() : transaction.getRefundAmount());
        if (refundAmount.compareTo(ZERO) <= 0 || refundAmount.compareTo(normalizeAmount(transaction.getAmount())) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        transaction.setStatus(PaymentTransactionStatus.REFUNDED);
        transaction.setRefundAmount(refundAmount);
        transaction.setRefundedAt(LocalDateTime.now());
        transaction.setFailureReason(null);
        paymentTransactionRepository.save(transaction);
        enqueueRefundSettlement(transaction, "completeRefund");
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PAYMENT_REFUND_COMPLETED.getMessage())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CinemaRevenueReportResponse getAllCinemaRevenueReport(CinemaRevenueReportRequest request) {
        validateRevenueReportRequest(request);
        List<CinemaGrpcClient.CinemaSummary> cinemas = cinemaGrpcClient.getAllActiveCinemas();
        return buildCinemaRevenueReport(cinemas, request);
    }

    @Override
    @Transactional(readOnly = true)
    public CinemaRevenueReportResponse getMyCinemaRevenueReport(CinemaRevenueReportRequest request,
            UUID requesterUserId) {
        validateRevenueReportRequest(request);
        if (requesterUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        List<CinemaGrpcClient.CinemaSummary> cinemas = cinemaGrpcClient.getCinemasByUserId(
                requesterUserId,
                HeaderNames.ROLE_MANAGER);
        return buildCinemaRevenueReport(cinemas, request);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportCinemaRevenueReport(CinemaRevenueReportRequest request, HttpServletRequest httpRequest) {
        validateRevenueReportRequest(request);
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);

        List<CinemaGrpcClient.CinemaSummary> cinemas;
        if (HeaderNames.ROLE_ADMIN.equals(role)) {
            cinemas = cinemaGrpcClient.getAllActiveCinemas();
        } else if (HeaderNames.ROLE_MANAGER.equals(role)) {
            UUID requesterUserId = RequestAuthUtils.requireUserId(httpRequest);
            List<UUID> accessibleCinemaIds = cinemaGrpcClient.getCinemasByUserId(
                    requesterUserId,
                    HeaderNames.ROLE_MANAGER)
                    .stream()
                    .map(CinemaGrpcClient.CinemaSummary::id)
                    .toList();
            if (accessibleCinemaIds == null || accessibleCinemaIds.isEmpty()) {
                throw new BusinessException(ErrorCode.MANAGER_NOT_ASSIGNED_CINEMA);
            }
            cinemas = cinemaGrpcClient.getAllActiveCinemas().stream()
                    .filter(cinema -> cinema != null
                            && cinema.id() != null
                            && accessibleCinemaIds.contains(cinema.id()))
                    .toList();
        } else {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<CinemaRevenueItemResponse> items = filterSelectedCinemaRevenueItems(
                loadCinemaRevenueItems(cinemas, request),
                request.getSelectedIds());

        return ExcelExportUtils.exportSingleSheet(
                "Báo cáo doanh thu",
                "BÁO CÁO DOANH THU THEO RẠP",
                List.of(
                        "Mã rạp",
                        "Tên rạp",
                        "Tổng giao dịch",
                        "Số giao dịch chờ thanh toán",
                        "Số giao dịch đã thanh toán",
                        "Số giao dịch thất bại",
                        "Số giao dịch hết hạn",
                        "Số giao dịch chờ hoàn tiền",
                        "Số giao dịch đã hoàn tiền",
                        "Tiền vé",
                        "Tiền sản phẩm",
                        "Mã khuyến mãi",
                        "Tên khuyến mãi",
                        "Tiền giảm giá",
                        "Tiền giảm từ điểm",
                        "Tiền đã thu",
                        "Tiền đã hoàn",
                        "Tổng giá trị giao dịch",
                        "Doanh thu thuần"),
                items.stream()
                        .map(item -> Arrays.asList(
                                item.cinemaId(),
                                item.cinemaName(),
                                item.totalTransactions(),
                                item.pendingCount(),
                                item.paidCount(),
                                item.failedCount(),
                                item.expiredCount(),
                                item.refundPendingCount(),
                                item.refundedCount(),
                                item.ticketSubtotalAmount(),
                                item.productSubtotalAmount(),
                                item.promotionCode(),
                                item.promotionName(),
                                item.promotionDiscountAmount(),
                                item.loyaltyPointsDiscountAmount(),
                                item.paidAmount(),
                                item.refundedAmount(),
                                item.grossAmount(),
                                item.netAmount()))
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public FilmRevenueReportResponse searchFilmRevenueReport(FilmRevenueReportRequest request,
            HttpServletRequest httpRequest) {
        validateFilmRevenueReportRequest(request);
        return buildFilmRevenueReport(request, httpRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportFilmRevenueReport(FilmRevenueReportRequest request, HttpServletRequest httpRequest) {
        validateFilmRevenueReportRequest(request);
        List<FilmRevenueItemResponse> selectedItems = filterSelectedFilmRevenueItems(
                loadFilmRevenueItems(request, httpRequest),
                request.getSelectedIds());

        return ExcelExportUtils.exportSingleSheet(
                "Báo cáo doanh thu phim",
                "BÁO CÁO DOANH THU THEO PHIM",
                List.of(
                        "Mã phim",
                        "Tên phim",
                        "Số rạp",
                        "Tổng giao dịch",
                        "Số giao dịch đã thanh toán",
                        "Số giao dịch đã hoàn tiền",
                        "Tiền đã thu"),
                selectedItems.stream()
                        .map(item -> Arrays.asList(
                                item.filmId(),
                                item.filmName(),
                                item.cinemaCount(),
                                item.totalTransactions(),
                                item.paidCount(),
                                item.refundedCount(),
                                item.paidAmount()))
                        .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionPreviewResponse previewPromotion(PromotionPreviewRequest request, UUID requesterUserId) {
        validateRequestedLoyaltyPoints(requesterUserId, request == null ? null : request.getLoyaltyPointsUsed());
        return promotionEngine.previewPromotion(request, requesterUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionSelectionResponse listSelectablePromotions(UUID bookingId, UUID requesterUserId) {
        if (bookingId == null || requesterUserId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        BookingGrpcClient.BookingPaymentContext bookingContext = bookingGrpcClient.getBookingPaymentContext(bookingId);
        ensureRequesterOwnsBooking(requesterUserId, bookingContext.userId());
        return promotionEngine.listSelectablePromotions(bookingId, requesterUserId);
    }

    @Override
    @Transactional
    public WebhookProcessingResult handleMomoWebhook(MomoIpnRequest request) {
        log.info(
                "MOMO_IPN_RECEIVED orderId={} requestId={} resultCode={} transId={} responseTime={}",
                request == null ? "" : safeWebhookPart(request.orderId()),
                request == null ? "" : safeWebhookPart(request.requestId()),
                request == null || request.resultCode() == null ? "" : request.resultCode(),
                request == null || request.transId() == null ? "" : request.transId(),
                request == null || request.responseTime() == null ? "" : request.responseTime());
        return processMomoCallback(request, "MOMO_IPN");
    }

    @Override
    @Transactional
    public WebhookProcessingResult handleMomoReturn(MomoIpnRequest request) {
        log.info(
                "MOMO_RETURN_RECEIVED orderId={} requestId={} resultCode={} transId={} responseTime={}",
                request == null ? "" : safeWebhookPart(request.orderId()),
                request == null ? "" : safeWebhookPart(request.requestId()),
                request == null || request.resultCode() == null ? "" : request.resultCode(),
                request == null || request.transId() == null ? "" : request.transId(),
                request == null || request.responseTime() == null ? "" : request.responseTime());
        return processMomoCallback(request, "MOMO_RETURN");
    }

    @Override
    @Transactional
    public void expireDueSessions() {
        List<PaymentTransaction> dueTransactions = paymentTransactionRepository.findAllByStatusAndExpiresAtBefore(
                PaymentTransactionStatus.PENDING,
                LocalDateTime.now());
        for (PaymentTransaction transaction : dueTransactions) {
            transaction.setStatus(PaymentTransactionStatus.EXPIRED);
            transaction.setExpiredAt(LocalDateTime.now());
            paymentTransactionRepository.save(transaction);
        }
    }

    private WebhookProcessingResult processMomoCallback(MomoIpnRequest request, String logPrefix) {
        if (request == null || !StringUtils.hasText(request.orderId()) || !StringUtils.hasText(request.signature())) {
            log.warn(
                    "{}_INVALID_PAYLOAD orderId={} requestId={} reason=missing_orderId_or_signature",
                    logPrefix,
                    request == null ? "" : safeWebhookPart(request.orderId()),
                    request == null ? "" : safeWebhookPart(request.requestId()));
            return new WebhookProcessingResult(HttpStatus.BAD_REQUEST, Map.of(
                    "success", false,
                    "message", "Invalid webhook payload"));
        }

        if (!verifyMomoSignature(request)) {
            log.warn(
                    "{}_REJECTED_SIGNATURE orderId={} requestId={} resultCode={}",
                    logPrefix,
                    safeWebhookPart(request.orderId()),
                    safeWebhookPart(request.requestId()),
                    request.resultCode());
            return new WebhookProcessingResult(HttpStatus.UNAUTHORIZED, Map.of(
                    "success", false,
                    "message", "Unauthorized"));
        }

        PaymentTransaction transaction = paymentTransactionRepository.findByOrderInvoiceNumber(request.orderId())
                .orElse(null);
        if (transaction == null) {
            log.info(
                    "{}_TX_NOT_FOUND orderId={} requestId={} resultCode={} transId={}",
                    logPrefix,
                    safeWebhookPart(request.orderId()),
                    safeWebhookPart(request.requestId()),
                    request.resultCode(),
                    request.transId());
            return new WebhookProcessingResult(HttpStatus.NO_CONTENT, Map.of(
                    "success", true,
                    "ignored", true,
                    "message", "Payment transaction not found"));
        }

        String webhookEventKey = buildWebhookEventKey(request);
        if (isDuplicateWebhook(transaction, webhookEventKey)) {
            log.info(
                    "{}_DUPLICATE orderId={} requestId={} transactionId={} webhookEventKey={}",
                    logPrefix,
                    safeWebhookPart(request.orderId()),
                    safeWebhookPart(request.requestId()),
                    transaction.getId(),
                    webhookEventKey);
            return new WebhookProcessingResult(HttpStatus.NO_CONTENT, Map.of(
                    "success", true,
                    "ignored", true,
                    "message", "Duplicate webhook ignored"));
        }

        if (transaction.getStatus() == PaymentTransactionStatus.EXPIRED) {
            log.info(
                    "{}_TX_ALREADY_EXPIRED orderId={} requestId={} transactionId={}",
                    logPrefix,
                    safeWebhookPart(request.orderId()),
                    safeWebhookPart(request.requestId()),
                    transaction.getId());
            markWebhookMeta(transaction, webhookEventKey);
            paymentTransactionRepository.save(transaction);
            return new WebhookProcessingResult(HttpStatus.NO_CONTENT, Map.of(
                    "success", true,
                    "ignored", true,
                    "message", "Payment session already expired"));
        }

        if (isSuccessfulMomoResult(request)) {
            log.info(
                    "{}_SUCCESS_RESULT orderId={} requestId={} transactionId={} resultCode={} amount={}",
                    logPrefix,
                    safeWebhookPart(request.orderId()),
                    safeWebhookPart(request.requestId()),
                    transaction.getId(),
                    request.resultCode(),
                    request.amount());
            return handlePaymentSucceeded(transaction, request, webhookEventKey, logPrefix);
        }

        if (transaction.getStatus() == PaymentTransactionStatus.PAID) {
            log.info(
                    "{}_ALREADY_PAID orderId={} requestId={} transactionId={}",
                    logPrefix,
                    safeWebhookPart(request.orderId()),
                    safeWebhookPart(request.requestId()),
                    transaction.getId());
            markWebhookMeta(transaction, webhookEventKey);
            paymentTransactionRepository.save(transaction);
            return new WebhookProcessingResult(HttpStatus.NO_CONTENT, Map.of(
                    "success", true,
                    "ignored", true,
                    "message", "Paid transaction already confirmed"));
        }

        transaction.setStatus(PaymentTransactionStatus.FAILED);
        transaction.setFailureReason(buildMomoFailureReason(request));
        log.warn(
                "{}_MARK_FAILED orderId={} requestId={} transactionId={} resultCode={} failureReason={}",
                logPrefix,
                safeWebhookPart(request.orderId()),
                safeWebhookPart(request.requestId()),
                transaction.getId(),
                request.resultCode(),
                transaction.getFailureReason());
        markWebhookMeta(transaction, webhookEventKey);
        paymentTransactionRepository.save(transaction);
        return new WebhookProcessingResult(HttpStatus.NO_CONTENT, Map.of(
                "success", true,
                "message", "Transaction marked as failed"));
    }

    private WebhookProcessingResult handlePaymentSucceeded(PaymentTransaction transaction,
            MomoIpnRequest request,
            String webhookEventKey,
            String logPrefix) {
        if (transaction.getStatus() == PaymentTransactionStatus.PAID) {
            enqueueLoyaltySync(transaction, "webhook:alreadyPaid");
            enqueuePaymentSettlement(transaction, "webhook:alreadyPaid");
            markWebhookMeta(transaction, webhookEventKey);
            paymentTransactionRepository.save(transaction);
            return new WebhookProcessingResult(HttpStatus.NO_CONTENT, Map.of(
                    "success", true,
                    "ignored", true,
                    "message", "Transaction already processed"));
        }
        if (transaction.getStatus() == PaymentTransactionStatus.REFUND_PENDING
                || transaction.getStatus() == PaymentTransactionStatus.REFUNDED) {
            markWebhookMeta(transaction, webhookEventKey);
            paymentTransactionRepository.save(transaction);
            return new WebhookProcessingResult(HttpStatus.NO_CONTENT, Map.of(
                    "success", true,
                    "ignored", true,
                    "message", "Transaction already processed"));
        }

        BigDecimal webhookAmount = request.amount() == null ? null
                : BigDecimal.valueOf(request.amount()).setScale(0, RoundingMode.HALF_UP);
        if (webhookAmount == null || transaction.getAmount() == null
                || transaction.getAmount().compareTo(webhookAmount) != 0) {
            log.warn(
                    "{}_AMOUNT_MISMATCH orderId={} requestId={} transactionId={} expectedAmount={} receivedAmount={}",
                    logPrefix,
                    safeWebhookPart(request.orderId()),
                    safeWebhookPart(request.requestId()),
                    transaction.getId(),
                    transaction.getAmount(),
                    webhookAmount);
            transaction.setStatus(PaymentTransactionStatus.FAILED);
            transaction.setFailureReason("AMOUNT_MISMATCH");
            markWebhookMeta(transaction, webhookEventKey);
            paymentTransactionRepository.save(transaction);
            return new WebhookProcessingResult(HttpStatus.NO_CONTENT, Map.of(
                    "success", true,
                    "ignored", true,
                    "message", "Amount mismatch"));
        }

        if (transaction.getExpiresAt() != null && !transaction.getExpiresAt().isAfter(LocalDateTime.now())) {
            log.info(
                    "{}_EXPIRED_BEFORE_CONFIRM orderId={} requestId={} transactionId={} expiresAt={}",
                    logPrefix,
                    safeWebhookPart(request.orderId()),
                    safeWebhookPart(request.requestId()),
                    transaction.getId(),
                    transaction.getExpiresAt());
            transaction.setStatus(PaymentTransactionStatus.EXPIRED);
            transaction.setExpiredAt(LocalDateTime.now());
            markWebhookMeta(transaction, webhookEventKey);
            paymentTransactionRepository.save(transaction);
            return new WebhookProcessingResult(HttpStatus.NO_CONTENT, Map.of(
                    "success", true,
                    "ignored", true,
                    "message", "Payment session expired"));
        }
        ensurePromotionUsageAvailable(transaction);

        transaction.setStatus(PaymentTransactionStatus.PAID);
        transaction.setPaidAt(parseTransactionDate(request));
        transaction.setProviderRef(extractProviderRef(request));
        transaction.setFailureReason(null);
        markWebhookMeta(transaction, webhookEventKey);
        paymentTransactionRepository.save(transaction);
        log.info(
                "{}_MARK_PAID orderId={} requestId={} transactionId={} bookingId={} amount={}",
                logPrefix,
                safeWebhookPart(request.orderId()),
                safeWebhookPart(request.requestId()),
                transaction.getId(),
                transaction.getBookingId(),
                transaction.getAmount());

        try {
            bookingGrpcClient.confirmBookingPayment(
                    transaction.getBookingId(),
                    transaction.getAmount(),
                    transaction.getPaymentMethod(),
                    transaction.getProviderRef(),
                    transaction.getOrderInvoiceNumber(),
                    transaction.getLoyaltyPointsUsed(),
                    transaction.getLoyaltyPointsEarned());
        } catch (BusinessException ex) {
            log.warn(
                    "{}_CONFIRM_BOOKING_FAILED orderId={} requestId={} transactionId={} bookingId={} errorCode={}",
                    logPrefix,
                    safeWebhookPart(request.orderId()),
                    safeWebhookPart(request.requestId()),
                    transaction.getId(),
                    transaction.getBookingId(),
                    ex.getErrorCode().name());
            transaction.setFailureReason("BOOKING_CONFIRM_FAILED:" + ex.getErrorCode().name());
            paymentTransactionRepository.save(transaction);
            return new WebhookProcessingResult(HttpStatus.NO_CONTENT, Map.of(
                    "success", true,
                    "message", "Payment recorded but booking confirmation failed",
                    "booking_error", ex.getErrorCode().name()));
        }

        enqueueLoyaltySync(transaction, "webhook");
        enqueuePaymentSettlement(transaction, "webhook");

        log.info(
                "{}_CONFIRMED orderId={} requestId={} transactionId={} bookingId={} status={}",
                logPrefix,
                safeWebhookPart(request.orderId()),
                safeWebhookPart(request.requestId()),
                transaction.getId(),
                transaction.getBookingId(),
                transaction.getStatus());
        return new WebhookProcessingResult(HttpStatus.NO_CONTENT, Map.of(
                "success", true,
                "message", "Payment confirmed"));
    }

    private void ensureBookable(BookingGrpcClient.BookingPaymentContext bookingContext) {
        if (bookingContext == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (bookingContext.reservedUntil() != null && !bookingContext.reservedUntil().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BOOKING_EXPIRED);
        }
        if ("EXPIRED".equalsIgnoreCase(bookingContext.bookingStatus())
                || "CANCELLED".equalsIgnoreCase(bookingContext.bookingStatus())) {
            throw new BusinessException(ErrorCode.BOOKING_EXPIRED);
        }
        if ("CONFIRMED".equalsIgnoreCase(bookingContext.bookingStatus())
                && !"PAID".equalsIgnoreCase(bookingContext.paymentStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private void ensureRequesterCanCreateSessionForBooking(UUID requesterUserId,
                                                           String requesterRole,
                                                           BookingGrpcClient.BookingPaymentContext bookingContext) {
        if (requesterUserId == null || bookingContext == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String normalizedRole = normalizeRole(requesterRole);
        if (HeaderNames.ROLE_ADMIN.equals(normalizedRole)) {
            return;
        }

        if (HeaderNames.ROLE_CUSTOMER.equals(normalizedRole)) {
            ensureRequesterOwnsBooking(requesterUserId, bookingContext.userId());
            return;
        }

        if (HeaderNames.ROLE_STAFF.equals(normalizedRole) || HeaderNames.ROLE_MANAGER.equals(normalizedRole)) {
            if (bookingContext.cinemaId() == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            List<UUID> accessibleCinemaIds = cinemaGrpcClient.getCinemasByUserId(requesterUserId, normalizedRole)
                    .stream()
                    .filter(cinema -> cinema != null && cinema.id() != null)
                    .map(CinemaGrpcClient.CinemaSummary::id)
                    .toList();
            if (!accessibleCinemaIds.contains(bookingContext.cinemaId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            return;
        }

        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private void ensureRequesterCanViewSession(PaymentTransaction transaction, UUID requesterUserId, String requesterRole) {
        if (transaction == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        String normalizedRole = normalizeRole(requesterRole);
        if (HeaderNames.ROLE_ADMIN.equals(normalizedRole)) {
            return;
        }

        if (HeaderNames.ROLE_CUSTOMER.equals(normalizedRole)) {
            ensureRequesterOwnsTransaction(transaction, requesterUserId);
            return;
        }

        if (HeaderNames.ROLE_STAFF.equals(normalizedRole) || HeaderNames.ROLE_MANAGER.equals(normalizedRole)) {
            ensureRequesterCanAccessCinemaScopedTransaction(transaction, requesterUserId, normalizedRole);
            return;
        }

        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private PaymentSessionResponse toPaymentSessionResponse(PaymentTransaction transaction) {
        return paymentMapper.toPaymentSessionResponse(
                transaction,
                readCheckoutFields(transaction.getCheckoutPayloadJson()),
                extractCheckoutResponseField(transaction.getResponsePayloadJson(), "qrCodeUrl"));
    }

    private void ensureRequesterCanCompleteSession(PaymentTransaction transaction, UUID requesterUserId, String requesterRole) {
        if (transaction == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        String normalizedRole = normalizeRole(requesterRole);
        if (HeaderNames.ROLE_ADMIN.equals(normalizedRole)) {
            return;
        }

        if (HeaderNames.ROLE_STAFF.equals(normalizedRole) || HeaderNames.ROLE_MANAGER.equals(normalizedRole)) {
            ensureRequesterCanAccessCinemaScopedTransaction(transaction, requesterUserId, normalizedRole);
            return;
        }

        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private void ensureRequesterCanAccessCinemaScopedTransaction(PaymentTransaction transaction,
                                                                 UUID requesterUserId,
                                                                 String normalizedRole) {
        if (requesterUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        UUID cinemaId = transaction.getCinemaId();
        if (cinemaId == null) {
            BookingGrpcClient.BookingPaymentContext bookingContext = bookingGrpcClient
                    .getBookingPaymentContext(transaction.getBookingId());
            if (bookingContext != null) {
                cinemaId = bookingContext.cinemaId();
            }
        }
        if (cinemaId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<UUID> accessibleCinemaIds = cinemaGrpcClient.getCinemasByUserId(requesterUserId, normalizedRole)
                .stream()
                .filter(cinema -> cinema != null && cinema.id() != null)
                .map(CinemaGrpcClient.CinemaSummary::id)
                .toList();
        if (!accessibleCinemaIds.contains(cinemaId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean requiresTransactionOwnershipCheck(String requesterRole) {
        return HeaderNames.ROLE_CUSTOMER.equals(normalizeRole(requesterRole));
    }

    private void ensureRequesterOwnsBooking(UUID requesterUserId, UUID bookingUserId) {
        if (requesterUserId == null || bookingUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!requesterUserId.equals(bookingUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void ensureRequesterOwnsTransaction(PaymentTransaction transaction, UUID requesterUserId) {
        if (requesterUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (transaction.getUserId() != null && !requesterUserId.equals(transaction.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (transaction.getUserId() == null) {
            BookingGrpcClient.BookingPaymentContext context = bookingGrpcClient
                    .getBookingPaymentContext(transaction.getBookingId());
            ensureRequesterOwnsBooking(requesterUserId, context.userId());
        }
    }

    private String normalizeRole(String role) {
        return StringUtils.hasText(role) ? role.trim().toUpperCase(Locale.ROOT) : "";
    }

    private long normalizeLongValue(Long value) {
        return value == null || value < 0 ? 0L : value;
    }

    private long normalizeRequestedLoyaltyPoints(Long value) {
        if (value == null) {
            return 0L;
        }
        if (value < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return value;
    }

    private boolean isReusable(PaymentTransaction latest, BookingGrpcClient.BookingPaymentContext bookingContext) {
        boolean momoPayment = latest.getPaymentMethod() != null
                && latest.getPaymentMethod().toUpperCase(Locale.ROOT).startsWith("MOMO");
        if (latest.getStatus() == PaymentTransactionStatus.PENDING) {
            return momoPayment
                    && latest.getExpiresAt() != null
                    && latest.getExpiresAt().isAfter(LocalDateTime.now());
        }
        if (latest.getStatus() == PaymentTransactionStatus.PAID) {
            return true;
        }
        return latest.getStatus() == PaymentTransactionStatus.EXPIRED
                && momoPayment
                && bookingContext.reservedUntil() != null
                && bookingContext.reservedUntil().isAfter(LocalDateTime.now());
    }

    private boolean canReuseLatestTransaction(PaymentTransaction latest,
                                              UUID requestedPromotionId,
                                              String requestedPromotionCode,
                                              long requestedLoyaltyPointsUsed) {
        String latestPromotionCode = normalizePromotionCode(latest.getPromotionCode());
        String normalizedRequestedPromotionCode = normalizePromotionCode(requestedPromotionCode);
        long latestLoyaltyPointsUsed = normalizeLongValue(latest.getLoyaltyPointsUsed());
        if (latestLoyaltyPointsUsed != requestedLoyaltyPointsUsed) {
            return false;
        }
        if (requestedPromotionId != null) {
            UUID latestPromotionId = paymentTransactionPromotionRepository
                    .findAllByPaymentTransactionIdOrderByApplyOrderAsc(latest.getId())
                    .stream()
                    .map(PaymentTransactionPromotion::getPromotionId)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (latestPromotionId != null) {
                return requestedPromotionId.equals(latestPromotionId);
            }
            return promotionRepository.findById(requestedPromotionId)
                    .filter(promotion -> !Boolean.TRUE.equals(promotion.getIsDeleted()))
                    .map(Promotion::getCode)
                    .map(this::normalizePromotionCode)
                    .map(latestPromotionCode::equals)
                    .orElse(false);
        }
        if (!StringUtils.hasText(normalizedRequestedPromotionCode)) {
            return true;
        }
        return normalizedRequestedPromotionCode.equals(latestPromotionCode);
    }

    private void applyLoyaltyPointsIfNeeded(PaymentTransaction transaction, long requestedLoyaltyPointsUsed) {
        if (transaction == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        long normalizedRequestedPoints = normalizeRequestedLoyaltyPoints(requestedLoyaltyPointsUsed);
        BigDecimal normalizedAmount = normalizeAmount(transaction.getAmount());
        if (transaction.getUserId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        UserGrpcClient.UserBasicInfo user = userGrpcClient.getUserBasicById(transaction.getUserId());
        applyCustomerRankSnapshot(transaction, user);
        if (normalizedRequestedPoints <= 0) {
            transaction.setLoyaltyPointsUsed(0L);
            transaction.setLoyaltyPointsEarned(calculateEarnedLoyaltyPoints(normalizedAmount, user));
            return;
        }

        long availablePoints = normalizeLongValue(user.loyaltyPoints());
        if (normalizedRequestedPoints > availablePoints) {
            throw new BusinessException(ErrorCode.LOYALTY_POINTS_INSUFFICIENT);
        }

        long maxRedeemablePoints = normalizedAmount.longValueExact();
        long appliedPoints = Math.min(normalizedRequestedPoints, maxRedeemablePoints);
        BigDecimal payableAmount = normalizedAmount.subtract(
                BigDecimal.valueOf(appliedPoints));
        transaction.setLoyaltyPointsUsed(appliedPoints);
        transaction.setAmount(normalizeAmount(payableAmount));
        transaction.setLoyaltyPointsEarned(calculateEarnedLoyaltyPoints(payableAmount, user));
    }

    private void enqueueLoyaltySync(PaymentTransaction transaction, String source) {
        if (transaction == null) {
            return;
        }

        paymentLoyaltyOutboxService.enqueueIfNeeded(transaction, source);
    }

    private void enqueuePaymentSettlement(PaymentTransaction transaction, String source) {
        if (transaction == null) {
            return;
        }
        paymentCustomerRankSettlementOutboxService.enqueueIfNeeded(
                transaction,
                normalizeAmount(transaction.getAmount()),
                "PAID",
                source);
    }

    private void enqueueRefundSettlement(PaymentTransaction transaction, String source) {
        if (transaction == null) {
            return;
        }
        BigDecimal refundAmount = normalizeAmount(
                transaction.getRefundAmount() == null ? transaction.getAmount() : transaction.getRefundAmount());
        paymentCustomerRankSettlementOutboxService.enqueueIfNeeded(
                transaction,
                refundAmount.negate(),
                "REFUNDED",
                source);
    }

    private void validateRequestedLoyaltyPoints(UUID userId, Long requestedLoyaltyPointsUsed) {
        long normalizedRequestedPoints = normalizeRequestedLoyaltyPoints(requestedLoyaltyPointsUsed);
        if (normalizedRequestedPoints <= 0) {
            return;
        }

        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        UserGrpcClient.UserBasicInfo user = userGrpcClient.getUserBasicById(userId);
        long availablePoints = normalizeLongValue(user.loyaltyPoints());
        if (normalizedRequestedPoints > availablePoints) {
            throw new BusinessException(ErrorCode.LOYALTY_POINTS_INSUFFICIENT);
        }
    }

    private long calculateEarnedLoyaltyPoints(BigDecimal amount, UserGrpcClient.UserBasicInfo user) {
        BigDecimal normalizedAmount = normalizeAmount(amount);
        BigDecimal earningAmountUnit = user == null
                ? LOYALTY_POINT_VALUE
                : normalizePositiveAmount(user.earningAmountUnit(), LOYALTY_POINT_VALUE);
        BigDecimal earningPointsPerUnit = user == null
                ? BigDecimal.ONE
                : normalizePositiveAmount(user.earningPointsPerUnit(), BigDecimal.ONE);
        return normalizedAmount
                .divide(earningAmountUnit, 8, RoundingMode.CEILING)
                .multiply(earningPointsPerUnit)
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();
    }

    private void applyCustomerRankSnapshot(PaymentTransaction transaction, UserGrpcClient.UserBasicInfo user) {
        if (transaction == null || user == null) {
            return;
        }
        transaction.setCustomerRankCode(normalizeStringValue(user.customerRankCode()));
        transaction.setCustomerRankName(normalizeStringValue(user.customerRankName()));
        transaction.setEarningAmountUnit(normalizePositiveAmount(user.earningAmountUnit(), LOYALTY_POINT_VALUE));
        transaction.setEarningPointsPerUnit(normalizePositiveAmount(user.earningPointsPerUnit(), BigDecimal.ONE));
    }

    private void ensurePromotionUsageAvailable(PaymentTransaction transaction) {
        if (transaction == null || transaction.getId() == null || transaction.getUserId() == null) {
            return;
        }

        List<PaymentTransactionPromotion> snapshots = paymentTransactionPromotionRepository
                .findAllByPaymentTransactionIdOrderByApplyOrderAsc(transaction.getId());
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }

        for (PaymentTransactionPromotion snapshot : snapshots) {
            if (snapshot == null || snapshot.getPromotionId() == null) {
                continue;
            }

            Promotion promotion = promotionRepository.findByIdForUpdate(snapshot.getPromotionId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST));
            if (Boolean.TRUE.equals(promotion.getIsDeleted())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            if (paymentTransactionPromotionRepository.existsReachedPaidUsageByPromotionIdAndUserId(
                    promotion.getId(),
                    transaction.getUserId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            if (promotion.getMaxUsageCount() != null) {
                long usedCount = paymentTransactionPromotionRepository
                        .countReachedPaidUsageByPromotionId(promotion.getId());
                if (usedCount >= promotion.getMaxUsageCount()) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST);
                }
            }
        }
    }

    private BigDecimal normalizePositiveAmount(BigDecimal value, BigDecimal fallback) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return fallback;
        }
        return value;
    }

    private List<PromotionQuote> applyPromotionIfNeeded(PaymentTransaction transaction,
                                                        UUID requestedPromotionId,
                                                        String requestedPromotionCode,
                                                        BookingGrpcClient.BookingPaymentContext bookingContext,
                                                        UUID requesterUserId) {
        if (requestedPromotionId == null && !StringUtils.hasText(requestedPromotionCode)) {
            transaction.setPromotionCode(null);
            transaction.setPromotionName(null);
            transaction.setPromotionDiscountAmount(ZERO);
            return List.of();
        }

        BigDecimal baseAmount = normalizeAmount(bookingContext.finalAmount());
        PromotionQuote quote = promotionEngine.resolvePromotionForCheckout(
                requestedPromotionId,
                requestedPromotionCode,
                baseAmount,
                bookingContext,
                bookingContext.userId());
        List<PromotionQuote> appliedPromotions = List.of(quote);
        transaction.setPromotionCode(joinPromotionCodes(appliedPromotions));
        transaction.setPromotionName(joinPromotionNames(appliedPromotions));
        transaction.setPromotionDiscountAmount(sumPromotionDiscountQuotes(appliedPromotions));
        transaction.setAmount(normalizeAmount(baseAmount.subtract(transaction.getPromotionDiscountAmount())));
        return appliedPromotions;
    }

    private void syncBookingPromotionSnapshot(BookingGrpcClient.BookingPaymentContext bookingContext,
                                              PaymentTransaction transaction,
                                              List<PromotionQuote> appliedPromotions) {
        if (bookingContext == null || transaction == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        UUID promotionId = resolvePromotionIdForSnapshot(transaction, appliedPromotions);
        bookingGrpcClient.upsertBookingPromotionSnapshot(
                bookingContext.bookingId(),
                promotionId,
                transaction.getPromotionCode(),
                transaction.getPromotionName(),
                normalizeAmount(transaction.getPromotionDiscountAmount()),
                normalizeAmount(transaction.getAmount()),
                transaction.getLoyaltyPointsUsed(),
                transaction.getLoyaltyPointsEarned());
    }

    private UUID resolvePromotionIdForSnapshot(PaymentTransaction transaction, List<PromotionQuote> appliedPromotions) {
        if (appliedPromotions != null) {
            return appliedPromotions.stream()
                    .filter(quote -> quote != null && StringUtils.hasText(quote.promotionCode()))
                    .map(PromotionQuote::promotionId)
                    .filter(id -> id != null)
                    .findFirst()
                    .orElse(null);
        }
        List<PaymentTransactionPromotion> snapshots = paymentTransactionPromotionRepository
                .findAllByPaymentTransactionIdOrderByApplyOrderAsc(transaction.getId());
        return snapshots.stream()
                .map(PaymentTransactionPromotion::getPromotionId)
                .filter(id -> id != null)
                .findFirst()
                .orElse(null);
    }

    private void savePromotionSnapshots(PaymentTransaction transaction, List<PromotionQuote> appliedPromotions) {
        if (transaction == null || transaction.getId() == null || appliedPromotions == null || appliedPromotions.isEmpty()) {
            return;
        }

        List<PaymentTransactionPromotion> snapshots = new ArrayList<>();
        int applyOrder = 1;
        for (PromotionQuote quote : appliedPromotions) {
            if (quote == null || !StringUtils.hasText(quote.promotionCode())) {
                continue;
            }
            PaymentTransactionPromotion snapshot = new PaymentTransactionPromotion();
            snapshot.setPaymentTransactionId(transaction.getId());
            snapshot.setPromotionId(quote.promotionId());
            snapshot.setPromotionCode(normalizeStringValue(quote.promotionCode()));
            snapshot.setPromotionName(normalizeStringValue(quote.promotionName()));
            snapshot.setDiscountAmount(normalizeAmount(quote.discountAmount()));
            snapshot.setApplyOrder(applyOrder++);
            snapshots.add(snapshot);
        }

        if (!snapshots.isEmpty()) {
            paymentTransactionPromotionRepository.saveAll(snapshots);
        }
    }

    private String joinPromotionCodes(List<PromotionQuote> appliedPromotions) {
        if (appliedPromotions == null || appliedPromotions.isEmpty()) {
            return null;
        }
        return appliedPromotions.stream()
                .map(PromotionQuote::promotionCode)
                .filter(StringUtils::hasText)
                .map(PaymentSessionServiceImpl::normalizeStringValue)
                .collect(Collectors.joining(", "));
    }

    private String joinPromotionNames(List<PromotionQuote> appliedPromotions) {
        if (appliedPromotions == null || appliedPromotions.isEmpty()) {
            return null;
        }
        return appliedPromotions.stream()
                .map(PromotionQuote::promotionName)
                .filter(StringUtils::hasText)
                .map(PaymentSessionServiceImpl::normalizeStringValue)
                .collect(Collectors.joining(", "));
    }

    private BigDecimal sumPromotionDiscountQuotes(List<PromotionQuote> appliedPromotions) {
        if (appliedPromotions == null || appliedPromotions.isEmpty()) {
            return ZERO;
        }
        BigDecimal total = ZERO;
        for (PromotionQuote quote : appliedPromotions) {
            if (quote == null) {
                continue;
            }
            total = total.add(normalizeAmount(quote.discountAmount()));
        }
        return normalizeAmount(total);
    }

    private static void appendPromotionTokens(List<String> target, String value) {
        if (target == null || !StringUtils.hasText(value)) {
            return;
        }
        target.add(value.trim());
    }

    private static String joinPromotionTokens(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.joining(", "));
    }

    private static String normalizeStringValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private Map<String, String> readCheckoutFields(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String extractCheckoutResponseField(String responseJson, String fieldName) {
        if (!StringUtils.hasText(responseJson) || !StringUtils.hasText(fieldName)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode value = root.path(fieldName);
            if (value.isMissingNode() || value.isNull()) {
                return null;
            }
            return value.isTextual() ? value.asText() : value.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return ZERO;
        }
        return amount.setScale(0, RoundingMode.HALF_UP);
    }

    private String buildInvoiceNumber(UUID bookingId) {
        return "PAY-" + bookingId.toString().replace("-", "") + "-" + System.currentTimeMillis();
    }

    private String extractProviderRef(MomoIpnRequest request) {
        if (request.transId() != null) {
            return String.valueOf(request.transId());
        }
        return request.orderId();
    }

    private LocalDateTime parseTransactionDate(MomoIpnRequest request) {
        if (request.responseTime() == null) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(request.responseTime()),
                    java.time.ZoneId.systemDefault());
        } catch (Exception ex) {
            return LocalDateTime.now();
        }
    }

    private String buildWebhookEventKey(MomoIpnRequest request) {
        String resultCode = safeWebhookPart(request.resultCode() == null ? null : String.valueOf(request.resultCode()));
        String orderId = safeWebhookPart(request.orderId());
        String requestId = safeWebhookPart(request.requestId());
        String transId = safeWebhookPart(request.transId() == null ? null : String.valueOf(request.transId()));
        return resultCode + "|" + orderId + "|" + requestId + "|" + transId;
    }

    private String safeWebhookPart(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return "";
        }
        return rawValue.trim();
    }

    private boolean isDuplicateWebhook(PaymentTransaction transaction, String webhookEventKey) {
        return StringUtils.hasText(webhookEventKey)
                && StringUtils.hasText(transaction.getWebhookEventKey())
                && webhookEventKey.equals(transaction.getWebhookEventKey());
    }

    private void markWebhookMeta(PaymentTransaction transaction, String webhookEventKey) {
        transaction.setWebhookEventKey(webhookEventKey);
        transaction.setLastWebhookAt(LocalDateTime.now());
    }

    private boolean verifyMomoSignature(MomoIpnRequest request) {
        String signed = buildMomoSignatureBase(request);
        String expectedSignature = hmacSha256Hex(signed, momoGatewayProperties.getSecretKey());
        return expectedSignature.equalsIgnoreCase(request.signature());
    }

    private boolean isSuccessfulMomoResult(MomoIpnRequest request) {
        return request.resultCode() != null && (request.resultCode() == 0 || request.resultCode() == 9000);
    }

    private String buildMomoFailureReason(MomoIpnRequest request) {
        if (request == null || request.resultCode() == null) {
            return "MOMO_RESULT_UNKNOWN";
        }
        return "MOMO_RESULT_" + request.resultCode();
    }

    private String buildMomoSignatureBase(MomoIpnRequest request) {
        StringBuilder signed = new StringBuilder();
        appendMomoSignaturePart(signed, "accessKey", momoGatewayProperties.getAccessKey());
        appendMomoSignaturePart(signed, "amount", request.amount() == null ? "" : String.valueOf(request.amount()));
        appendMomoSignaturePart(signed, "extraData", safeWebhookPart(request.extraData()));
        appendMomoSignaturePart(signed, "message", safeWebhookPart(request.message()));
        appendMomoSignaturePart(signed, "orderId", safeWebhookPart(request.orderId()));
        appendMomoSignaturePart(signed, "orderInfo", safeWebhookPart(request.orderInfo()));
        appendMomoSignaturePart(signed, "orderType", safeWebhookPart(request.orderType()));
        appendMomoSignaturePart(signed, "partnerCode", safeWebhookPart(request.partnerCode()));
        appendMomoSignaturePart(signed, "payType", safeWebhookPart(request.payType()));
        appendMomoSignaturePart(signed, "requestId", safeWebhookPart(request.requestId()));
        appendMomoSignaturePart(signed, "responseTime",
                request.responseTime() == null ? "" : String.valueOf(request.responseTime()));
        appendMomoSignaturePart(signed, "resultCode",
                request.resultCode() == null ? "" : String.valueOf(request.resultCode()));
        appendMomoSignaturePart(signed, "transId", request.transId() == null ? "" : String.valueOf(request.transId()));
        return signed.toString();
    }

    private void appendMomoSignaturePart(StringBuilder signed, String key, String value) {
        if (signed.length() > 0) {
            signed.append('&');
        }
        signed.append(key).append('=').append(value == null ? "" : value);
    }

    private String hmacSha256Hex(String value, String secretKey) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    "HmacSHA256"));
            byte[] digest = mac.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String normalizePromotionCode(String code) {
        if (!StringUtils.hasText(code)) {
            return "";
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private void validateRevenueReportRequest(CinemaRevenueReportRequest request) {
        if (request == null || request.getPageRequest() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        DateRange dateRange = request.getDateRange();
        if (dateRange != null
                && dateRange.getFrom() != null
                && dateRange.getTo() != null
                && dateRange.getTo().isBefore(dateRange.getFrom())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private CinemaRevenueReportResponse buildCinemaRevenueReport(
            List<CinemaGrpcClient.CinemaSummary> cinemas,
            CinemaRevenueReportRequest request) {
        DateRange dateRange = request.getDateRange();
        LocalDateTime from = dateRange == null ? null : dateRange.getFrom();
        LocalDateTime to = dateRange == null ? null : dateRange.getTo();
        PageRequest<CinemaRevenueField> pageRequest = request.getPageRequest();
        List<CinemaRevenueItemResponse> filteredItems = loadCinemaRevenueItems(cinemas, request);
        int page = pageRequest.getPageOrDefault();
        int size = pageRequest.getSizeOrDefault();
        long totalElements = filteredItems.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        List<CinemaRevenueItemResponse> pageItems = paginate(filteredItems, page, size);

        return CinemaRevenueReportResponse.builder()
                .from(from)
                .to(to)
                .generatedAt(LocalDateTime.now())
                .currentPage(page)
                .totalPages(totalPages)
                .totalElements(totalElements)
                .size(size)
                .hasNext(page < totalPages)
                .hasPrevious(page > 1)
                .items(pageItems)
                .page(toSummary(pageItems))
                .total(toSummary(filteredItems))
                .build();
    }

    private List<CinemaRevenueItemResponse> loadCinemaRevenueItems(
            List<CinemaGrpcClient.CinemaSummary> cinemas,
            CinemaRevenueReportRequest request) {
        DateRange dateRange = request.getDateRange();
        LocalDateTime from = dateRange == null ? null : dateRange.getFrom();
        LocalDateTime to = dateRange == null ? null : dateRange.getTo();
        List<UUID> requestedCinemaIds = normalizeUuidList(request.getCinemaIds());
        List<UUID> requestedFilmIds = normalizeUuidList(request.getFilmIds());
        List<CinemaGrpcClient.CinemaSummary> scopeCinemas = resolveCinemaRevenueScope(cinemas, requestedCinemaIds);
        if (scopeCinemas.isEmpty()) {
            return List.of();
        }

        List<PaymentTransaction> revenueTransactions = paymentTransactionRepositoryImpl.findAllForRevenueReport(
                scopeCinemas.stream().map(CinemaGrpcClient.CinemaSummary::id).toList(),
                requestedFilmIds,
                from,
                to);
        List<UUID> transactionIds = revenueTransactions.stream()
                .map(PaymentTransaction::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<PaymentTransactionPromotion> promotionSnapshots = transactionIds.isEmpty()
                ? List.of()
                : paymentTransactionPromotionRepository.findAllByPaymentTransactionIdIn(
                        transactionIds,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Order.asc("paymentTransactionId"),
                                org.springframework.data.domain.Sort.Order.asc("applyOrder"),
                                org.springframework.data.domain.Sort.Order.asc("timeCreated"),
                                org.springframework.data.domain.Sort.Order.asc("id")));
        return applyCinemaRevenuePageRequest(
                revenueReportSupport.aggregateCinemaRevenueItems(
                        scopeCinemas,
                        request,
                        revenueTransactions,
                        promotionSnapshots),
                request.getPageRequest());
    }

    private List<CinemaGrpcClient.CinemaSummary> resolveCinemaRevenueScope(
            List<CinemaGrpcClient.CinemaSummary> cinemas,
            List<UUID> requestedCinemaIds) {
        List<CinemaGrpcClient.CinemaSummary> scopeCinemas = cinemas == null ? List.of() : new ArrayList<>(cinemas);
        return scopeCinemas.stream()
                .filter(cinema -> cinema != null && cinema.id() != null)
                .filter(cinema -> requestedCinemaIds == null || requestedCinemaIds.contains(cinema.id()))
                .distinct()
                .toList();
    }

    private List<CinemaRevenueItemResponse> filterSelectedCinemaRevenueItems(
            List<CinemaRevenueItemResponse> items,
            List<UUID> selectedIds) {
        List<UUID> normalizedSelectedIds = normalizeUuidList(selectedIds);
        if (normalizedSelectedIds == null) {
            return items == null ? List.of() : new ArrayList<>(items);
        }
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(item -> item != null
                        && item.cinemaId() != null
                        && normalizedSelectedIds.contains(item.cinemaId()))
                .toList();
    }

    private void validateFilmRevenueReportRequest(FilmRevenueReportRequest request) {
        if (request == null || request.getPageRequest() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        DateRange dateRange = request.getDateRange();
        if (dateRange != null
                && dateRange.getFrom() != null
                && dateRange.getTo() != null
                && dateRange.getTo().isBefore(dateRange.getFrom())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private FilmRevenueReportResponse buildFilmRevenueReport(
            FilmRevenueReportRequest request,
            HttpServletRequest httpRequest) {
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        List<UUID> scopeCinemaIds = resolveFilmRevenueCinemaScope(httpRequest, role, request.getCinemaIds());
        DateRange dateRange = request.getDateRange();
        LocalDateTime from = dateRange == null ? null : dateRange.getFrom();
        LocalDateTime to = dateRange == null ? null : dateRange.getTo();

        List<FilmRevenueItemResponse> allItems = loadFilmRevenueItems(request, scopeCinemaIds);
        List<FilmRevenueItemResponse> filteredItems = applyFilmRevenuePageRequest(
                allItems,
                request.getPageRequest());
        int page = request.getPageRequest().getPageOrDefault();
        int size = request.getPageRequest().getSizeOrDefault();
        long totalElements = filteredItems.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        List<FilmRevenueItemResponse> pageItems = paginate(filteredItems, page, size);

        return FilmRevenueReportResponse.builder()
                .from(from)
                .to(to)
                .generatedAt(LocalDateTime.now())
                .currentPage(page)
                .totalPages(totalPages)
                .totalElements(totalElements)
                .size(size)
                .hasNext(page < totalPages)
                .hasPrevious(page > 1)
                .items(pageItems)
                .page(toFilmSummary(pageItems))
                .total(toFilmSummary(filteredItems))
                .build();
    }

    private List<FilmRevenueItemResponse> loadFilmRevenueItems(
            FilmRevenueReportRequest request,
            HttpServletRequest httpRequest) {
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        List<UUID> scopeCinemaIds = resolveFilmRevenueCinemaScope(httpRequest, role, request.getCinemaIds());
        return loadFilmRevenueItems(request, scopeCinemaIds);
    }

    private List<FilmRevenueItemResponse> loadFilmRevenueItems(
            FilmRevenueReportRequest request,
            List<UUID> scopeCinemaIds) {
        DateRange dateRange = request.getDateRange();
        LocalDateTime from = dateRange == null ? null : dateRange.getFrom();
        LocalDateTime to = dateRange == null ? null : dateRange.getTo();
        List<UUID> requestedFilmIds = normalizeUuidList(request.getFilmIds());

        List<PaymentTransaction> revenueTransactions = paymentTransactionRepositoryImpl.findAllForRevenueReport(
                scopeCinemaIds,
                requestedFilmIds,
                from,
                to);
        Map<UUID, String> filmNames = revenueTransactions.isEmpty()
                ? Map.of()
                : filmGrpcClient.getFilmTitlesByIds(
                        revenueTransactions.stream()
                                .map(PaymentTransaction::getFilmId)
                                .filter(java.util.Objects::nonNull)
                                .distinct()
                                .toList());
        return revenueReportSupport.aggregateFilmRevenueItems(revenueTransactions, filmNames, request);
    }

    private List<FilmRevenueItemResponse> filterSelectedFilmRevenueItems(
            List<FilmRevenueItemResponse> items,
            List<UUID> selectedIds) {
        List<UUID> normalizedSelectedIds = normalizeUuidList(selectedIds);
        if (normalizedSelectedIds == null) {
            return items == null ? List.of() : new ArrayList<>(items);
        }
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(item -> item != null
                        && item.filmId() != null
                        && normalizedSelectedIds.contains(item.filmId()))
                .toList();
    }

    private List<UUID> resolveFilmRevenueCinemaScope(
            HttpServletRequest httpRequest,
            String role,
            List<UUID> requestedCinemaIds) {
        String normalizedRole = normalizeRole(role);
        List<UUID> accessibleCinemaIds;
        if (HeaderNames.ROLE_ADMIN.equals(normalizedRole)) {
            accessibleCinemaIds = cinemaGrpcClient.getAllActiveCinemas().stream()
                    .filter(cinema -> cinema != null && cinema.id() != null)
                    .map(CinemaGrpcClient.CinemaSummary::id)
                    .toList();
        } else if (HeaderNames.ROLE_MANAGER.equals(normalizedRole) || HeaderNames.ROLE_STAFF.equals(normalizedRole)) {
            UUID requesterUserId = RequestAuthUtils.requireUserId(httpRequest);
            accessibleCinemaIds = cinemaGrpcClient.getCinemasByUserId(requesterUserId, normalizedRole).stream()
                    .filter(cinema -> cinema != null && cinema.id() != null)
                    .map(CinemaGrpcClient.CinemaSummary::id)
                    .toList();
            if (accessibleCinemaIds.isEmpty()) {
                throw new BusinessException(ErrorCode.MANAGER_NOT_ASSIGNED_CINEMA);
            }
        } else {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<UUID> normalizedRequestedIds = normalizeUuidList(requestedCinemaIds);
        if (normalizedRequestedIds == null) {
            return accessibleCinemaIds;
        }
        return accessibleCinemaIds.stream()
                .filter(normalizedRequestedIds::contains)
                .distinct()
                .toList();
    }

    private List<FilmRevenueItemResponse> applyFilmRevenuePageRequest(
            List<FilmRevenueItemResponse> items,
            PageRequest<FilmRevenueField> request) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<FilmRevenueItemResponse> filtered = new ArrayList<>(items);
        String keyword = request.getNormalizedKeyword();
        if (keyword != null) {
            filtered = filtered.stream()
                    .filter(item -> matchesKeyword(item, keyword))
                    .toList();
            filtered = new ArrayList<>(filtered);
        }

        List<FilterField<FilmRevenueField>> filters = request.getFilterBy();
        if (filters != null && !filters.isEmpty()) {
            filtered = filtered.stream()
                    .filter(item -> matchesAllFilters(item, filters))
                    .toList();
            filtered = new ArrayList<>(filtered);
        }

        filtered.sort(buildFilmRevenueComparator(request.getSortBy()));
        return filtered;
    }

    private Comparator<FilmRevenueItemResponse> buildFilmRevenueComparator(
            List<SortField<FilmRevenueField>> sortFields) {
        Comparator<FilmRevenueItemResponse> defaultComparator = Comparator
                .comparing(FilmRevenueItemResponse::paidAmount, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(FilmRevenueItemResponse::filmName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(FilmRevenueItemResponse::filmId, Comparator.nullsLast(Comparator.naturalOrder()));

        if (sortFields == null || sortFields.isEmpty()) {
            return defaultComparator;
        }

        Comparator<FilmRevenueItemResponse> merged = null;
        for (SortField<FilmRevenueField> sort : sortFields) {
            if (sort == null || sort.getField() == null) {
                continue;
            }
            Comparator<FilmRevenueItemResponse> fieldComparator = (left, right) -> compareValues(
                    getFilmRevenueFieldValue(left, sort.getField()),
                    getFilmRevenueFieldValue(right, sort.getField()));
            if ("DESC".equalsIgnoreCase(FilmRevenueField.normalizeDirection(sort.getDirection()))) {
                fieldComparator = fieldComparator.reversed();
            }
            merged = merged == null ? fieldComparator : merged.thenComparing(fieldComparator);
        }
        return merged == null ? defaultComparator : merged.thenComparing(defaultComparator);
    }

    private boolean matchesKeyword(FilmRevenueItemResponse item, String keyword) {
        if (!StringUtils.hasText(keyword) || item == null) {
            return true;
        }
        return SearchTextUtils.containsIgnoreCase(item.filmName(), keyword)
                || SearchTextUtils.containsIgnoreCase(item.filmId() == null ? null : item.filmId().toString(), keyword);
    }

    private boolean matchesAllFilters(
            FilmRevenueItemResponse item,
            List<FilterField<FilmRevenueField>> filters) {
        for (FilterField<FilmRevenueField> filter : filters) {
            if (!matchesFilter(item, filter)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesFilter(FilmRevenueItemResponse item, FilterField<FilmRevenueField> filter) {
        if (item == null || filter == null || filter.getField() == null || filter.getOperator() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        String operator = filter.getOperator().trim().toUpperCase(Locale.ROOT);
        Object rawValue = filter.getValue();
        Object fieldValue = getFilmRevenueFieldValue(item, filter.getField());
        Class<?> dataType = filter.getField().getDataType();

        return switch (operator) {
            case "EQ" ->
                compareValues(fieldValue, FilmRevenueField.convertValue(String.valueOf(rawValue), dataType)) == 0;
            case "NEQ" ->
                compareValues(fieldValue, FilmRevenueField.convertValue(String.valueOf(rawValue), dataType)) != 0;
            case "LIKE" -> fieldValue instanceof String text
                    && SearchTextUtils.containsIgnoreCase(text, String.valueOf(rawValue));
            case "GTE" ->
                compareValues(fieldValue, FilmRevenueField.convertValue(String.valueOf(rawValue), dataType)) >= 0;
            case "LTE" ->
                compareValues(fieldValue, FilmRevenueField.convertValue(String.valueOf(rawValue), dataType)) <= 0;
            case "IN" -> matchesInValues(fieldValue, rawValue, dataType);
            case "BETWEEN" -> matchesBetweenValues(fieldValue, rawValue, dataType);
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        };
    }

    private Object getFilmRevenueFieldValue(FilmRevenueItemResponse item, FilmRevenueField field) {
        return switch (field) {
            case FILM_ID -> item.filmId();
            case FILM_NAME -> item.filmName();
            case CINEMA_COUNT -> item.cinemaCount();
            case TOTAL_TRANSACTIONS -> item.totalTransactions();
            case PAID_COUNT -> item.paidCount();
            case REFUNDED_COUNT -> item.refundedCount();
            case PAID_AMOUNT -> item.paidAmount();
        };
    }

    private FilmRevenueSummaryResponse toFilmSummary(List<FilmRevenueItemResponse> items) {
        if (items == null || items.isEmpty()) {
            return FilmRevenueSummaryResponse.builder()
                    .cinemaCount(0)
                    .totalTransactions(0)
                    .paidCount(0)
                    .refundedCount(0)
                    .paidAmount(ZERO)
                    .build();
        }

        long cinemaCount = 0;
        long totalTransactions = 0;
        long paidCount = 0;
        long refundedCount = 0;
        BigDecimal paidAmount = ZERO;

        for (FilmRevenueItemResponse item : items) {
            if (item == null) {
                continue;
            }
            cinemaCount += item.cinemaCount();
            totalTransactions += item.totalTransactions();
            paidCount += item.paidCount();
            refundedCount += item.refundedCount();
            paidAmount = paidAmount.add(normalizeAmount(item.paidAmount()));
        }

        return FilmRevenueSummaryResponse.builder()
                .cinemaCount(cinemaCount)
                .totalTransactions(totalTransactions)
                .paidCount(paidCount)
                .refundedCount(refundedCount)
                .paidAmount(normalizeAmount(paidAmount))
                .build();
    }

    private List<UUID> normalizeUuidList(Collection<UUID> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<UUID> normalized = values.stream()
                .filter(value -> value != null)
                .distinct()
                .toList();
        return normalized.isEmpty() ? null : normalized;
    }

    private List<CinemaRevenueItemResponse> applyCinemaRevenuePageRequest(
            List<CinemaRevenueItemResponse> items,
            PageRequest<CinemaRevenueField> request) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<CinemaRevenueItemResponse> filtered = new ArrayList<>(items);
        String keyword = request.getNormalizedKeyword();
        if (keyword != null) {
            filtered = filtered.stream()
                    .filter(item -> matchesKeyword(item, keyword))
                    .toList();
            filtered = new ArrayList<>(filtered);
        }

        List<FilterField<CinemaRevenueField>> filters = request.getFilterBy();
        if (filters != null && !filters.isEmpty()) {
            filtered = filtered.stream()
                    .filter(item -> matchesAllFilters(item, filters))
                    .toList();
            filtered = new ArrayList<>(filtered);
        }

        filtered.sort(buildCinemaRevenueComparator(request.getSortBy()));
        return filtered;
    }

    private Comparator<CinemaRevenueItemResponse> buildCinemaRevenueComparator(
            List<SortField<CinemaRevenueField>> sortFields) {
        Comparator<CinemaRevenueItemResponse> comparator = Comparator
                .comparing(CinemaRevenueItemResponse::cinemaName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(CinemaRevenueItemResponse::cinemaId, Comparator.nullsLast(Comparator.naturalOrder()));

        if (sortFields == null || sortFields.isEmpty()) {
            return comparator;
        }

        Comparator<CinemaRevenueItemResponse> merged = null;
        for (SortField<CinemaRevenueField> sort : sortFields) {
            if (sort == null || sort.getField() == null) {
                continue;
            }
            Comparator<CinemaRevenueItemResponse> fieldComparator = (left, right) -> compareValues(
                    getCinemaRevenueFieldValue(left, sort.getField()),
                    getCinemaRevenueFieldValue(right, sort.getField()));
            if ("DESC".equalsIgnoreCase(sort.getDirection())) {
                fieldComparator = fieldComparator.reversed();
            }
            merged = merged == null ? fieldComparator : merged.thenComparing(fieldComparator);
        }
        return merged == null ? comparator : merged;
    }

    private boolean matchesKeyword(CinemaRevenueItemResponse item, String keyword) {
        if (!StringUtils.hasText(keyword) || item == null) {
            return true;
        }
        return SearchTextUtils.containsIgnoreCase(item.cinemaName(), keyword)
                || SearchTextUtils.containsIgnoreCase(item.cinemaId() == null ? null : item.cinemaId().toString(), keyword);
    }

    private boolean matchesAllFilters(
            CinemaRevenueItemResponse item,
            List<FilterField<CinemaRevenueField>> filters) {
        for (FilterField<CinemaRevenueField> filter : filters) {
            if (!matchesFilter(item, filter)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesFilter(CinemaRevenueItemResponse item, FilterField<CinemaRevenueField> filter) {
        if (item == null || filter == null || filter.getField() == null || filter.getOperator() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        String operator = filter.getOperator().trim().toUpperCase(Locale.ROOT);
        Object rawValue = filter.getValue();
        Object fieldValue = getCinemaRevenueFieldValue(item, filter.getField());
        Class<?> dataType = filter.getField().getDataType();

        return switch (operator) {
            case "EQ" ->
                compareValues(fieldValue, CinemaRevenueField.convertValue(String.valueOf(rawValue), dataType)) == 0;
            case "NEQ" ->
                compareValues(fieldValue, CinemaRevenueField.convertValue(String.valueOf(rawValue), dataType)) != 0;
            case "LIKE" -> fieldValue instanceof String text
                    && SearchTextUtils.containsIgnoreCase(text, String.valueOf(rawValue));
            case "GTE" ->
                compareValues(fieldValue, CinemaRevenueField.convertValue(String.valueOf(rawValue), dataType)) >= 0;
            case "LTE" ->
                compareValues(fieldValue, CinemaRevenueField.convertValue(String.valueOf(rawValue), dataType)) <= 0;
            case "IN" -> matchesInValues(fieldValue, rawValue, dataType);
            case "BETWEEN" -> matchesBetweenValues(fieldValue, rawValue, dataType);
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        };
    }

    private boolean matchesInValues(Object fieldValue, Object rawValue, Class<?> dataType) {
        List<Comparable<?>> values = convertMultipleValues(rawValue, dataType);
        for (Comparable<?> value : values) {
            if (compareValues(fieldValue, value) == 0) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesBetweenValues(Object fieldValue, Object rawValue, Class<?> dataType) {
        List<Comparable<?>> values = convertMultipleValues(rawValue, dataType);
        if (values.size() != 2) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Comparable<?> start = values.get(0);
        Comparable<?> end = values.get(1);
        if (compareValues(start, end) > 0) {
            Comparable<?> tmp = start;
            start = end;
            end = tmp;
        }
        return compareValues(fieldValue, start) >= 0 && compareValues(fieldValue, end) <= 0;
    }

    private List<Comparable<?>> convertMultipleValues(Object rawValue, Class<?> dataType) {
        if (rawValue == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        List<Comparable<?>> values = new ArrayList<>();
        if (rawValue instanceof Collection<?> collection) {
            for (Object item : collection) {
                values.add(CinemaRevenueField.convertValue(String.valueOf(item), dataType));
            }
            return values;
        }

        if (rawValue.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(rawValue);
            for (int i = 0; i < length; i++) {
                values.add(CinemaRevenueField.convertValue(String.valueOf(java.lang.reflect.Array.get(rawValue, i)),
                        dataType));
            }
            return values;
        }

        String text = String.valueOf(rawValue).trim();
        if (text.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        for (String token : text.split(",")) {
            String value = token.trim();
            if (value.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            values.add(CinemaRevenueField.convertValue(value, dataType));
        }
        return values;
    }

    private Object getCinemaRevenueFieldValue(CinemaRevenueItemResponse item, CinemaRevenueField field) {
        return switch (field) {
            case CINEMA_ID -> item.cinemaId();
            case CINEMA_NAME -> item.cinemaName();
            case TOTAL_TRANSACTIONS -> item.totalTransactions();
            case PENDING_COUNT -> item.pendingCount();
            case PAID_COUNT -> item.paidCount();
            case FAILED_COUNT -> item.failedCount();
            case EXPIRED_COUNT -> item.expiredCount();
            case REFUND_PENDING_COUNT -> item.refundPendingCount();
            case REFUNDED_COUNT -> item.refundedCount();
            case TICKET_SUBTOTAL_AMOUNT -> item.ticketSubtotalAmount();
            case PRODUCT_SUBTOTAL_AMOUNT -> item.productSubtotalAmount();
            case PROMOTION_CODE -> item.promotionCode();
            case PROMOTION_NAME -> item.promotionName();
            case PROMOTION_DISCOUNT_AMOUNT -> item.promotionDiscountAmount();
            case LOYALTY_POINTS_DISCOUNT_AMOUNT -> item.loyaltyPointsDiscountAmount();
            case PAID_AMOUNT -> item.paidAmount();
            case REFUNDED_AMOUNT -> item.refundedAmount();
            case GROSS_AMOUNT -> item.grossAmount();
            case NET_AMOUNT -> item.netAmount();
        };
    }

    private int compareValues(Object left, Object right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return new BigDecimal(leftNumber.toString()).compareTo(new BigDecimal(rightNumber.toString()));
        }
        if (left instanceof Comparable<?> comparableLeft && right instanceof Comparable<?>) {
            @SuppressWarnings("unchecked")
            Comparable<Object> typedLeft = (Comparable<Object>) comparableLeft;
            return typedLeft.compareTo(right);
        }
        return String.valueOf(left).compareTo(String.valueOf(right));
    }

    private <T> List<T> paginate(List<T> items, int page, int size) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        int fromIndex = Math.max(0, (page - 1) * size);
        if (fromIndex >= items.size()) {
            return List.of();
        }
        int toIndex = Math.min(items.size(), fromIndex + size);
        return new ArrayList<>(items.subList(fromIndex, toIndex));
    }

    private CinemaRevenueSummaryResponse toSummary(List<CinemaRevenueItemResponse> items) {
        if (items == null || items.isEmpty()) {
            return CinemaRevenueSummaryResponse.builder()
                    .totalTransactions(0)
                    .pendingCount(0)
                    .paidCount(0)
                    .failedCount(0)
                    .expiredCount(0)
                    .refundPendingCount(0)
                    .refundedCount(0)
                    .ticketSubtotalAmount(ZERO)
                    .productSubtotalAmount(ZERO)
                    .promotionCode("")
                    .promotionName("")
                    .promotionDiscountAmount(ZERO)
                    .loyaltyPointsDiscountAmount(ZERO)
                    .paidAmount(ZERO)
                    .refundedAmount(ZERO)
                    .grossAmount(ZERO)
                    .netAmount(ZERO)
                    .build();
        }

        long totalTransactions = 0;
        long pendingCount = 0;
        long paidCount = 0;
        long failedCount = 0;
        long expiredCount = 0;
        long refundPendingCount = 0;
        long refundedCount = 0;
        BigDecimal ticketSubtotalAmount = ZERO;
        BigDecimal productSubtotalAmount = ZERO;
        BigDecimal promotionDiscountAmount = ZERO;
        BigDecimal loyaltyPointsDiscountAmount = ZERO;
        BigDecimal paidAmount = ZERO;
        BigDecimal refundedAmount = ZERO;
        BigDecimal grossAmount = ZERO;
        BigDecimal netAmount = ZERO;
        List<String> promotionCodes = new ArrayList<>();
        List<String> promotionNames = new ArrayList<>();

        for (CinemaRevenueItemResponse item : items) {
            if (item == null) {
                continue;
            }
            totalTransactions += item.totalTransactions();
            pendingCount += item.pendingCount();
            paidCount += item.paidCount();
            failedCount += item.failedCount();
            expiredCount += item.expiredCount();
            refundPendingCount += item.refundPendingCount();
            refundedCount += item.refundedCount();
            ticketSubtotalAmount = ticketSubtotalAmount.add(normalizeAmount(item.ticketSubtotalAmount()));
            productSubtotalAmount = productSubtotalAmount.add(normalizeAmount(item.productSubtotalAmount()));
            appendPromotionTokens(promotionCodes, item.promotionCode());
            appendPromotionTokens(promotionNames, item.promotionName());
            promotionDiscountAmount = promotionDiscountAmount.add(normalizeAmount(item.promotionDiscountAmount()));
            loyaltyPointsDiscountAmount = loyaltyPointsDiscountAmount.add(
                    normalizeAmount(item.loyaltyPointsDiscountAmount()));
            paidAmount = paidAmount.add(normalizeAmount(item.paidAmount()));
            refundedAmount = refundedAmount.add(normalizeAmount(item.refundedAmount()));
            grossAmount = grossAmount.add(normalizeAmount(item.grossAmount()));
            netAmount = netAmount.add(normalizeAmount(item.netAmount()));
        }

        return CinemaRevenueSummaryResponse.builder()
                .totalTransactions(totalTransactions)
                .pendingCount(pendingCount)
                .paidCount(paidCount)
                .failedCount(failedCount)
                .expiredCount(expiredCount)
                .refundPendingCount(refundPendingCount)
                .refundedCount(refundedCount)
                .ticketSubtotalAmount(ticketSubtotalAmount)
                .productSubtotalAmount(productSubtotalAmount)
                .promotionCode(joinPromotionTokens(promotionCodes))
                .promotionName(joinPromotionTokens(promotionNames))
                .promotionDiscountAmount(promotionDiscountAmount)
                .loyaltyPointsDiscountAmount(loyaltyPointsDiscountAmount)
                .paidAmount(paidAmount)
                .refundedAmount(refundedAmount)
                .grossAmount(grossAmount)
                .netAmount(netAmount)
                .build();
    }

}
