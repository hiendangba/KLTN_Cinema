package com.cinema.payment_service.services.impl;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.payment_service.config.MomoGatewayProperties;
import com.cinema.payment_service.dto.request.CinemaRevenueField;
import com.cinema.payment_service.dto.request.CinemaRevenueReportRequest;
import com.cinema.payment_service.dto.request.CreatePaymentSessionRequest;
import com.cinema.payment_service.dto.request.PaymentSessionField;
import com.cinema.payment_service.dto.request.PromotionPreviewRequest;
import com.cinema.payment_service.dto.request.RefundPaymentRequest;
import com.cinema.payment_service.dto.momo.MomoIpnRequest;
import com.cinema.payment_service.dto.response.CinemaRevenueItemResponse;
import com.cinema.payment_service.dto.response.CinemaRevenueReportResponse;
import com.cinema.payment_service.dto.response.CinemaRevenueSummaryResponse;
import com.cinema.payment_service.dto.response.PaymentReconciliationResponse;
import com.cinema.payment_service.dto.response.PaymentSessionResponse;
import com.cinema.payment_service.dto.response.PromotionPreviewResponse;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.request.DateRange;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.SortField;
import com.cinema.dto.response.PageResponse;
import com.cinema.excel.ExcelExportUtils;
import com.cinema.payment_service.entity.PaymentTransaction;
import com.cinema.payment_service.entity.PaymentTransactionPromotion;
import com.cinema.payment_service.enums.PaymentTransactionStatus;
import com.cinema.payment_service.grpc.CinemaGrpcClient;
import com.cinema.payment_service.grpc.BookingGrpcClient;
import com.cinema.payment_service.repository.PaymentTransactionRepository;
import com.cinema.payment_service.repository.PaymentTransactionRepositoryImpl;
import com.cinema.payment_service.repository.PaymentTransactionPromotionRepository;
import com.cinema.payment_service.services.PaymentSessionService;
import com.cinema.payment_service.support.MomoPaymentGatewayClient;
import com.cinema.payment_service.support.PromotionEngine;
import com.cinema.payment_service.support.PromotionQuote;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
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

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentTransactionRepositoryImpl paymentTransactionRepositoryImpl;
    private final BookingGrpcClient bookingGrpcClient;
    private final CinemaGrpcClient cinemaGrpcClient;
    private final PaymentTransactionPromotionRepository paymentTransactionPromotionRepository;
    private final MomoGatewayProperties momoGatewayProperties;
    private final MomoPaymentGatewayClient momoPaymentGatewayClient;
    private final PromotionEngine promotionEngine;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public PaymentSessionResponse createSession(CreatePaymentSessionRequest request, UUID requesterUserId) {
        if (request == null || request.getBookingId() == null || requesterUserId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        UUID bookingId = request.getBookingId();
        String requestedPromotionCode = normalizePromotionCode(request.getPromotionCode());
        BookingGrpcClient.BookingPaymentContext bookingContext = bookingGrpcClient.getBookingPaymentContext(bookingId);
        ensureRequesterOwnsBooking(requesterUserId, bookingContext.userId());
        ensureBookable(bookingContext);

        if ("PAID".equalsIgnoreCase(bookingContext.paymentStatus())
                || "CONFIRMED".equalsIgnoreCase(bookingContext.bookingStatus())) {
            return paymentTransactionRepository.findFirstByBookingIdAndStatusOrderByTimeCreatedDesc(
                    bookingId,
                    PaymentTransactionStatus.PAID)
                    .map(this::toResponse)
                    .orElseGet(() -> paymentTransactionRepository.findFirstByBookingIdOrderByTimeCreatedDesc(bookingId)
                            .map(this::toResponse)
                            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)));
        }

        PaymentTransaction latest = paymentTransactionRepository.findFirstByBookingIdOrderByTimeCreatedDesc(bookingId)
                .orElse(null);
        if (latest != null) {
            ensureRequesterOwnsTransaction(latest, requesterUserId);
            if (isReusable(latest, bookingContext) && canReuseLatestTransaction(latest, requestedPromotionCode)) {
                syncBookingPromotionSnapshot(bookingContext, latest, null);
                return toResponse(latest);
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
                requestedPromotionCode,
                bookingContext,
                requesterUserId);
        syncBookingPromotionSnapshot(bookingContext, transaction, appliedPromotions);

        MomoPaymentGatewayClient.MomoCheckoutResult checkoutResult = momoPaymentGatewayClient.createCheckout(
                transaction,
                bookingContext);
        transaction.setPayUrl(checkoutResult.payUrl());
        transaction.setCheckoutPayloadJson(checkoutResult.requestPayloadJson());
        PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);
        savePromotionSnapshots(savedTransaction, appliedPromotions);
        return toResponse(savedTransaction, checkoutResult.qrCodeUrl());
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentSessionResponse getSession(UUID bookingId, UUID requesterUserId) {
        if (bookingId == null || requesterUserId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        PaymentTransaction transaction = paymentTransactionRepository
                .findFirstByBookingIdOrderByTimeCreatedDesc(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        ensureRequesterOwnsTransaction(transaction, requesterUserId);
        return toResponse(transaction);
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
                .data(sessions.stream().map(this::toResponse).toList())
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
    public PaymentSessionResponse requestRefund(UUID bookingId, UUID requesterUserId, RefundPaymentRequest request) {
        if (bookingId == null || requesterUserId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        PaymentTransaction transaction = paymentTransactionRepository
                .findFirstByBookingIdOrderByTimeCreatedDesc(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        ensureRequesterOwnsTransaction(transaction, requesterUserId);

        if (transaction.getStatus() == PaymentTransactionStatus.REFUND_PENDING
                || transaction.getStatus() == PaymentTransactionStatus.REFUNDED) {
            return toResponse(transaction);
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
        return toResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentReconciliationResponse getReconciliation(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        List<PaymentTransaction> transactions = paymentTransactionRepository.findAllByTimeCreatedBetween(from, to);
        long pendingCount = 0;
        long paidCount = 0;
        long failedCount = 0;
        long expiredCount = 0;
        long refundPendingCount = 0;
        long refundedCount = 0;
        BigDecimal paidAmount = ZERO;
        BigDecimal refundedAmount = ZERO;

        for (PaymentTransaction transaction : transactions) {
            if (transaction.getStatus() == null) {
                continue;
            }
            switch (transaction.getStatus()) {
                case PENDING -> pendingCount++;
                case PAID -> {
                    paidCount++;
                    paidAmount = paidAmount.add(normalizeAmount(transaction.getAmount()));
                }
                case FAILED -> failedCount++;
                case EXPIRED -> expiredCount++;
                case REFUND_PENDING -> refundPendingCount++;
                case REFUNDED -> {
                    refundedCount++;
                    refundedAmount = refundedAmount.add(normalizeAmount(transaction.getRefundAmount()));
                }
            }
        }

        return PaymentReconciliationResponse.builder()
                .from(from)
                .to(to)
                .totalTransactions(transactions.size())
                .pendingCount(pendingCount)
                .paidCount(paidCount)
                .failedCount(failedCount)
                .expiredCount(expiredCount)
                .refundPendingCount(refundPendingCount)
                .refundedCount(refundedCount)
                .paidAmount(paidAmount)
                .refundedAmount(refundedAmount)
                .netAmount(paidAmount.subtract(refundedAmount))
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
                aggregateCinemaRevenueItems(cinemas, request),
                request.getSelectedIds());

        return ExcelExportUtils.exportSingleSheet(
                "Payment Revenue",
                List.of(
                        "Cinema ID",
                        "Cinema Name",
                        "Total Transactions",
                        "Pending Count",
                        "Paid Count",
                        "Failed Count",
                        "Expired Count",
                        "Refund Pending Count",
                        "Refunded Count",
                        "Ticket Subtotal Amount",
                        "Product Subtotal Amount",
                        "Promotion Code",
                        "Promotion Name",
                        "Promotion Discount Amount",
                        "Paid Amount",
                        "Refunded Amount",
                        "Gross Amount",
                        "Net Amount"),
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
                                item.paidAmount(),
                                item.refundedAmount(),
                                item.grossAmount(),
                                item.netAmount()))
                        .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionPreviewResponse previewPromotion(PromotionPreviewRequest request, UUID requesterUserId) {
        return promotionEngine.previewPromotion(request, requesterUserId);
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

        if (request == null || !StringUtils.hasText(request.orderId()) || !StringUtils.hasText(request.signature())) {
            log.warn(
                    "MOMO_IPN_INVALID_PAYLOAD orderId={} requestId={} reason=missing_orderId_or_signature",
                    request == null ? "" : safeWebhookPart(request.orderId()),
                    request == null ? "" : safeWebhookPart(request.requestId()));
            return new WebhookProcessingResult(HttpStatus.BAD_REQUEST, Map.of(
                    "success", false,
                    "message", "Invalid webhook payload"));
        }

        if (!verifyMomoSignature(request)) {
            log.warn(
                    "MOMO_IPN_REJECTED_SIGNATURE orderId={} requestId={} resultCode={}",
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
                    "MOMO_IPN_TX_NOT_FOUND orderId={} requestId={} resultCode={} transId={}",
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
                    "MOMO_IPN_DUPLICATE orderId={} requestId={} transactionId={} webhookEventKey={}",
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
                    "MOMO_IPN_TX_ALREADY_EXPIRED orderId={} requestId={} transactionId={}",
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
                    "MOMO_IPN_SUCCESS_RESULT orderId={} requestId={} transactionId={} resultCode={} amount={}",
                    safeWebhookPart(request.orderId()),
                    safeWebhookPart(request.requestId()),
                    transaction.getId(),
                    request.resultCode(),
                    request.amount());
            return handlePaymentSucceeded(transaction, request, webhookEventKey);
        }

        if (transaction.getStatus() == PaymentTransactionStatus.PAID) {
            log.info(
                    "MOMO_IPN_ALREADY_PAID orderId={} requestId={} transactionId={}",
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
                "MOMO_IPN_MARK_FAILED orderId={} requestId={} transactionId={} resultCode={} failureReason={}",
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

    private WebhookProcessingResult handlePaymentSucceeded(PaymentTransaction transaction,
            MomoIpnRequest request,
            String webhookEventKey) {
        if (transaction.getStatus() == PaymentTransactionStatus.PAID
                || transaction.getStatus() == PaymentTransactionStatus.REFUND_PENDING
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
                    "MOMO_IPN_AMOUNT_MISMATCH orderId={} requestId={} transactionId={} expectedAmount={} receivedAmount={}",
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
                    "MOMO_IPN_EXPIRED_BEFORE_CONFIRM orderId={} requestId={} transactionId={} expiresAt={}",
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

        transaction.setStatus(PaymentTransactionStatus.PAID);
        transaction.setPaidAt(parseTransactionDate(request));
        transaction.setProviderRef(extractProviderRef(request));
        transaction.setFailureReason(null);
        markWebhookMeta(transaction, webhookEventKey);
        paymentTransactionRepository.save(transaction);
        log.info(
                "MOMO_IPN_MARK_PAID orderId={} requestId={} transactionId={} bookingId={} amount={}",
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
                    transaction.getOrderInvoiceNumber());
        } catch (BusinessException ex) {
            log.warn(
                    "MOMO_IPN_CONFIRM_BOOKING_FAILED orderId={} requestId={} transactionId={} bookingId={} errorCode={}",
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

        log.info(
                "MOMO_IPN_CONFIRMED orderId={} requestId={} transactionId={} bookingId={} status={}",
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

    private boolean canReuseLatestTransaction(PaymentTransaction latest, String requestedPromotionCode) {
        String latestPromotionCode = normalizePromotionCode(latest.getPromotionCode());
        if (!StringUtils.hasText(requestedPromotionCode)) {
            return true;
        }
        return requestedPromotionCode.equals(latestPromotionCode);
    }

    private List<PromotionQuote> applyPromotionIfNeeded(PaymentTransaction transaction,
                                                        String requestedPromotionCode,
                                                        BookingGrpcClient.BookingPaymentContext bookingContext,
                                                        UUID requesterUserId) {
        if (!StringUtils.hasText(requestedPromotionCode)) {
            transaction.setPromotionCode(null);
            transaction.setPromotionName(null);
            transaction.setPromotionDiscountAmount(ZERO);
            return List.of();
        }

        BigDecimal baseAmount = normalizeAmount(bookingContext.finalAmount());
        PromotionQuote quote = promotionEngine.resolvePromotionForCheckout(
                requestedPromotionCode,
                baseAmount,
                bookingContext,
                requesterUserId);
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
                normalizeAmount(transaction.getAmount()));
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

    private BigDecimal sumPromotionDiscountSnapshots(List<PromotionSnapshotView> promotionSnapshots) {
        if (promotionSnapshots == null || promotionSnapshots.isEmpty()) {
            return ZERO;
        }
        BigDecimal total = ZERO;
        for (PromotionSnapshotView snapshot : promotionSnapshots) {
            if (snapshot == null) {
                continue;
            }
            total = total.add(normalizeAmount(snapshot.discountAmount()));
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

    private PaymentSessionResponse toResponse(PaymentTransaction transaction) {
        return toResponse(transaction, null);
    }

    private PaymentSessionResponse toResponse(PaymentTransaction transaction, String qrCodeUrl) {
        return PaymentSessionResponse.builder()
                .id(transaction.getId())
                .bookingId(transaction.getBookingId())
                .showtimeId(transaction.getShowtimeId())
                .cinemaId(transaction.getCinemaId())
                .userId(transaction.getUserId())
                .amount(transaction.getAmount())
                .ticketSubtotalSnapshot(transaction.getTicketSubtotalSnapshot())
                .productSubtotalSnapshot(transaction.getProductSubtotalSnapshot())
                .currency(transaction.getCurrency())
                .paymentMethod(transaction.getPaymentMethod())
                .orderInvoiceNumber(transaction.getOrderInvoiceNumber())
                .providerRef(transaction.getProviderRef())
                .payUrl(transaction.getPayUrl())
                .qrCodeUrl(qrCodeUrl)
                .checkoutFields(readCheckoutFields(transaction.getCheckoutPayloadJson()))
                .status(transaction.getStatus())
                .expiresAt(transaction.getExpiresAt())
                .paidAt(transaction.getPaidAt())
                .expiredAt(transaction.getExpiredAt())
                .failureReason(transaction.getFailureReason())
                .refundAmount(transaction.getRefundAmount())
                .refundReason(transaction.getRefundReason())
                .refundedAt(transaction.getRefundedAt())
                .promotionCode(transaction.getPromotionCode())
                .promotionName(transaction.getPromotionName())
                .promotionDiscountAmount(transaction.getPromotionDiscountAmount())
                .build();
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
        List<CinemaRevenueItemResponse> filteredItems = aggregateCinemaRevenueItems(cinemas, request);
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

    private List<CinemaRevenueItemResponse> aggregateCinemaRevenueItems(
            List<CinemaGrpcClient.CinemaSummary> cinemas,
            CinemaRevenueReportRequest request) {
        DateRange dateRange = request.getDateRange();
        LocalDateTime from = dateRange == null ? null : dateRange.getFrom();
        LocalDateTime to = dateRange == null ? null : dateRange.getTo();
        List<UUID> requestedCinemaIds = normalizeUuidList(request.getCinemaIds());
        List<UUID> requestedFilmIds = normalizeUuidList(request.getFilmIds());
        List<CinemaGrpcClient.CinemaSummary> scopeCinemas = cinemas == null ? List.of() : new ArrayList<>(cinemas);
        scopeCinemas = scopeCinemas.stream()
                .filter(cinema -> cinema != null && cinema.id() != null)
                .filter(cinema -> requestedCinemaIds == null || requestedCinemaIds.contains(cinema.id()))
                .distinct()
                .toList();

        List<CinemaRevenueItemResponse> allItems = initializeRevenueItems(scopeCinemas);
        if (allItems.isEmpty()) {
            return List.of();
        }

        Map<UUID, CinemaRevenueAccumulator> accumulatorMap = allItems.stream()
                .collect(java.util.stream.Collectors.toMap(
                        CinemaRevenueItemResponse::cinemaId,
                        item -> new CinemaRevenueAccumulator(item.cinemaId(), item.cinemaName()),
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<PaymentTransaction> revenueTransactions = paymentTransactionRepositoryImpl.findAllForRevenueReport(
                scopeCinemas.stream().map(CinemaGrpcClient.CinemaSummary::id).toList(),
                requestedFilmIds,
                from,
                to);
        Map<UUID, List<PromotionSnapshotView>> promotionSnapshotsByTransactionId = loadPromotionSnapshots(
                revenueTransactions.stream()
                        .map(PaymentTransaction::getId)
                        .filter(java.util.Objects::nonNull)
                        .toList());

        for (PaymentTransaction transaction : revenueTransactions) {
            CinemaRevenueAccumulator accumulator = accumulatorMap.get(transaction.getCinemaId());
            if (accumulator == null) {
                continue;
            }
            applyTransactionToAccumulator(
                    accumulator,
                    transaction,
                    promotionSnapshotsByTransactionId.get(transaction.getId()),
                    from,
                    to);
        }

        List<CinemaRevenueItemResponse> allFilteredItems = accumulatorMap.values().stream()
                .map(CinemaRevenueAccumulator::toResponse)
                .toList();
        return applyCinemaRevenuePageRequest(allFilteredItems, request.getPageRequest());
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

    private List<CinemaRevenueItemResponse> initializeRevenueItems(List<CinemaGrpcClient.CinemaSummary> cinemas) {
        if (cinemas == null || cinemas.isEmpty()) {
            return List.of();
        }
        return cinemas.stream()
                .map(cinema -> CinemaRevenueItemResponse.builder()
                        .cinemaId(cinema.id())
                        .cinemaName(cinema.name())
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
                        .paidAmount(ZERO)
                        .refundedAmount(ZERO)
                        .grossAmount(ZERO)
                        .netAmount(ZERO)
                        .build())
                .toList();
    }

    private void applyTransactionToAccumulator(
            CinemaRevenueAccumulator accumulator,
            PaymentTransaction transaction,
            List<PromotionSnapshotView> promotionSnapshots,
            LocalDateTime from,
            LocalDateTime to) {
        if (transaction == null) {
            return;
        }

        if (isBetween(transaction.getPaidAt(), from, to)) {
            BigDecimal amount = normalizeAmount(transaction.getAmount());
            List<PromotionSnapshotView> resolvedPromotionSnapshots = resolvePromotionSnapshots(
                    transaction,
                    promotionSnapshots);
            accumulator.totalTransactions++;
            accumulator.paidCount++;
            accumulator.paidAmount = accumulator.paidAmount.add(amount);
            accumulator.appendPromotionSnapshots(resolvedPromotionSnapshots);
            accumulator.promotionDiscountAmount = accumulator.promotionDiscountAmount.add(
                    sumPromotionDiscountSnapshots(resolvedPromotionSnapshots));
            accumulator.grossAmount = accumulator.grossAmount.add(amount);
            accumulator.netAmount = accumulator.netAmount.add(amount);
            accumulator.ticketSubtotalAmount = accumulator.ticketSubtotalAmount.add(
                    normalizeAmount(transaction.getTicketSubtotalSnapshot()));
            accumulator.productSubtotalAmount = accumulator.productSubtotalAmount.add(
                    normalizeAmount(transaction.getProductSubtotalSnapshot()));
        }

        if (isBetween(transaction.getRefundedAt(), from, to)) {
            BigDecimal refundAmount = transaction.getRefundAmount() != null
                    ? normalizeAmount(transaction.getRefundAmount())
                    : normalizeAmount(transaction.getAmount());
            accumulator.totalTransactions++;
            accumulator.refundedCount++;
            accumulator.refundedAmount = accumulator.refundedAmount.add(refundAmount);
            accumulator.netAmount = accumulator.netAmount.subtract(refundAmount);
            accumulator.ticketSubtotalAmount = accumulator.ticketSubtotalAmount.subtract(
                    normalizeAmount(transaction.getTicketSubtotalSnapshot()));
            accumulator.productSubtotalAmount = accumulator.productSubtotalAmount.subtract(
                    normalizeAmount(transaction.getProductSubtotalSnapshot()));
        }
    }

    private Map<UUID, List<PromotionSnapshotView>> loadPromotionSnapshots(Collection<UUID> paymentTransactionIds) {
        if (paymentTransactionIds == null || paymentTransactionIds.isEmpty()) {
            return Map.of();
        }

        List<PaymentTransactionPromotion> snapshots = paymentTransactionPromotionRepository
                .findAllByPaymentTransactionIdIn(
                        paymentTransactionIds,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Order.asc("paymentTransactionId"),
                                org.springframework.data.domain.Sort.Order.asc("applyOrder"),
                                org.springframework.data.domain.Sort.Order.asc("timeCreated"),
                                org.springframework.data.domain.Sort.Order.asc("id")));
        if (snapshots == null || snapshots.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<PromotionSnapshotView>> grouped = new LinkedHashMap<>();
        for (PaymentTransactionPromotion snapshot : snapshots) {
            if (snapshot == null || snapshot.getPaymentTransactionId() == null) {
                continue;
            }
            grouped.computeIfAbsent(snapshot.getPaymentTransactionId(), key -> new ArrayList<>())
                    .add(new PromotionSnapshotView(
                            snapshot.getPromotionId(),
                            normalizeStringValue(snapshot.getPromotionCode()),
                            normalizeStringValue(snapshot.getPromotionName()),
                            normalizeAmount(snapshot.getDiscountAmount()),
                            snapshot.getApplyOrder(),
                            snapshot.getTimeCreated()));
        }
        return grouped;
    }

    private List<PromotionSnapshotView> resolvePromotionSnapshots(
            PaymentTransaction transaction,
            List<PromotionSnapshotView> promotionSnapshots) {
        if (promotionSnapshots != null && !promotionSnapshots.isEmpty()) {
            return promotionSnapshots;
        }

        if (transaction == null || !StringUtils.hasText(transaction.getPromotionCode())) {
            return List.of();
        }

        return List.of(new PromotionSnapshotView(
                null,
                normalizeStringValue(transaction.getPromotionCode()),
                normalizeStringValue(transaction.getPromotionName()),
                normalizeAmount(transaction.getPromotionDiscountAmount()),
                1,
                transaction.getTimeCreated()));
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
        String normalized = keyword.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(item.cinemaName(), normalized)
                || containsIgnoreCase(item.cinemaId() == null ? null : item.cinemaId().toString(), normalized);
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
                    && containsIgnoreCase(text, String.valueOf(rawValue).toLowerCase(Locale.ROOT));
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

    private boolean containsIgnoreCase(String value, String keyword) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(keyword)) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private boolean isBetween(LocalDateTime value, LocalDateTime from, LocalDateTime to) {
        if (value == null || from == null || to == null) {
            if (value == null) {
                return false;
            }
            if (from == null && to == null) {
                return true;
            }
            if (from == null) {
                return !value.isAfter(to);
            }
            if (to == null) {
                return !value.isBefore(from);
            }
            return false;
        }
        return !value.isBefore(from) && !value.isAfter(to);
    }

    private List<CinemaRevenueItemResponse> paginate(List<CinemaRevenueItemResponse> items, int page, int size) {
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
                .paidAmount(paidAmount)
                .refundedAmount(refundedAmount)
                .grossAmount(grossAmount)
                .netAmount(netAmount)
                .build();
    }

    private static class CinemaRevenueAccumulator {
        private final UUID cinemaId;
        private final String cinemaName;
        private long totalTransactions;
        private long pendingCount;
        private long paidCount;
        private long failedCount;
        private long expiredCount;
        private long refundPendingCount;
        private long refundedCount;
        private BigDecimal ticketSubtotalAmount = ZERO;
        private BigDecimal productSubtotalAmount = ZERO;
        private final List<String> promotionCodes = new ArrayList<>();
        private final List<String> promotionNames = new ArrayList<>();
        private BigDecimal promotionDiscountAmount = ZERO;
        private BigDecimal paidAmount = ZERO;
        private BigDecimal refundedAmount = ZERO;
        private BigDecimal grossAmount = ZERO;
        private BigDecimal netAmount = ZERO;

        private CinemaRevenueAccumulator(UUID cinemaId, String cinemaName) {
            this.cinemaId = cinemaId;
            this.cinemaName = cinemaName;
        }

        private CinemaRevenueItemResponse toResponse() {
            return CinemaRevenueItemResponse.builder()
                    .cinemaId(cinemaId)
                    .cinemaName(cinemaName)
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
                    .paidAmount(paidAmount)
                    .refundedAmount(refundedAmount)
                    .grossAmount(grossAmount)
                    .netAmount(netAmount)
                    .build();
        }

        private void appendPromotionSnapshots(List<PromotionSnapshotView> snapshots) {
            if (snapshots == null || snapshots.isEmpty()) {
                return;
            }
            for (PromotionSnapshotView snapshot : snapshots) {
                if (snapshot == null) {
                    continue;
                }
                appendPromotionTokens(promotionCodes, snapshot.promotionCode());
                appendPromotionTokens(promotionNames, snapshot.promotionName());
            }
        }
    }

    private record PromotionSnapshotView(
            UUID promotionId,
            String promotionCode,
            String promotionName,
            BigDecimal discountAmount,
            Integer applyOrder,
            LocalDateTime timeCreated) {
    }
}
