package com.cinema.payment_service.services.impl;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.payment_service.config.SePayGatewayProperties;
import com.cinema.payment_service.dto.request.CreatePaymentSessionRequest;
import com.cinema.payment_service.dto.request.PromotionPreviewRequest;
import com.cinema.payment_service.dto.request.RefundPaymentRequest;
import com.cinema.payment_service.dto.response.PaymentReconciliationResponse;
import com.cinema.payment_service.dto.response.PaymentSessionResponse;
import com.cinema.payment_service.dto.response.PromotionPreviewResponse;
import com.cinema.payment_service.dto.webhook.SePayIpnRequest;
import com.cinema.payment_service.entity.PaymentTransaction;
import com.cinema.payment_service.enums.PaymentTransactionStatus;
import com.cinema.payment_service.grpc.BookingGrpcClient;
import com.cinema.payment_service.repository.PaymentTransactionRepository;
import com.cinema.payment_service.services.PaymentSessionService;
import com.cinema.payment_service.support.SePayCheckoutFormFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentSessionServiceImpl implements PaymentSessionService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(0, RoundingMode.HALF_UP);
    private static final BigDecimal TEN_PERCENT = new BigDecimal("0.10");
    private static final BigDecimal FIFTEEN_PERCENT = new BigDecimal("0.15");
    private static final BigDecimal THIRTY_THOUSAND = new BigDecimal("30000");
    private static final BigDecimal FORTY_THOUSAND = new BigDecimal("40000");
    private static final BigDecimal TWENTY_THOUSAND = new BigDecimal("20000");
    private static final BigDecimal MIN_FOR_CINEMASTAR10 = new BigDecimal("100000");
    private static final BigDecimal MIN_FOR_COMBO20K = new BigDecimal("150000");

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final BookingGrpcClient bookingGrpcClient;
    private final SePayGatewayProperties sePayGatewayProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public PaymentSessionResponse createSession(CreatePaymentSessionRequest request, UUID requesterUserId) {
        if (request == null || request.getBookingId() == null || requesterUserId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        UUID bookingId = request.getBookingId();
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
            if (isReusable(latest, bookingContext)) {
                return toResponse(latest);
            }
        }

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setBookingId(bookingContext.bookingId());
        transaction.setShowtimeId(bookingContext.showtimeId());
        transaction.setUserId(bookingContext.userId());
        transaction.setAmount(normalizeAmount(bookingContext.finalAmount()));
        transaction.setCurrency("VND");
        transaction.setPaymentMethod(sePayGatewayProperties.getPaymentMethod());
        transaction.setOrderInvoiceNumber(buildInvoiceNumber(bookingContext.bookingId()));
        transaction.setStatus(PaymentTransactionStatus.PENDING);
        transaction.setExpiresAt(bookingContext.reservedUntil());

        SePayCheckoutFormFactory.CheckoutForm checkoutForm = SePayCheckoutFormFactory.build(
                sePayGatewayProperties,
                transaction,
                bookingContext);
        transaction.setCheckoutUrl(checkoutForm.checkoutUrl());
        transaction.setCheckoutPayloadJson(writeJson(checkoutForm.fields()));
        paymentTransactionRepository.save(transaction);
        return toResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentSessionResponse getSession(UUID bookingId, UUID requesterUserId) {
        if (bookingId == null || requesterUserId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        PaymentTransaction transaction = paymentTransactionRepository.findFirstByBookingIdOrderByTimeCreatedDesc(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        ensureRequesterOwnsTransaction(transaction, requesterUserId);
        return toResponse(transaction);
    }

    @Override
    @Transactional
    public PaymentSessionResponse requestRefund(UUID bookingId, UUID requesterUserId, RefundPaymentRequest request) {
        if (bookingId == null || requesterUserId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        PaymentTransaction transaction = paymentTransactionRepository.findFirstByBookingIdOrderByTimeCreatedDesc(bookingId)
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
                request != null && request.getRefundAmount() != null ? request.getRefundAmount() : transaction.getAmount());
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
    public PromotionPreviewResponse previewPromotion(PromotionPreviewRequest request, UUID requesterUserId) {
        if (request == null || requesterUserId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        BigDecimal baseAmount = request.getOrderAmount();
        if (baseAmount == null && request.getBookingId() != null) {
            BookingGrpcClient.BookingPaymentContext bookingContext = bookingGrpcClient.getBookingPaymentContext(request.getBookingId());
            ensureRequesterOwnsBooking(requesterUserId, bookingContext.userId());
            baseAmount = bookingContext.finalAmount();
        }
        if (baseAmount == null || baseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        BigDecimal normalizedBaseAmount = normalizeAmount(baseAmount);
        String promotionCode = normalizePromotionCode(request.getPromotionCode());
        PromotionCalculation promotion = calculatePromotion(normalizedBaseAmount, promotionCode);

        return PromotionPreviewResponse.builder()
                .promotionCode(promotion.code())
                .originalAmount(normalizedBaseAmount)
                .discountAmount(promotion.discount())
                .finalAmount(normalizedBaseAmount.subtract(promotion.discount()))
                .note(promotion.note())
                .build();
    }

    @Override
    @Transactional
    public WebhookProcessingResult handleSePayWebhook(String secretKey, SePayIpnRequest request) {
        if (!StringUtils.hasText(secretKey) || !secretKey.equals(sePayGatewayProperties.getSecretKey())) {
            return new WebhookProcessingResult(HttpStatus.UNAUTHORIZED, Map.of(
                    "success", false,
                    "message", "Unauthorized"));
        }
        if (request == null || request.order() == null || !StringUtils.hasText(request.order().orderInvoiceNumber())) {
            return new WebhookProcessingResult(HttpStatus.BAD_REQUEST, Map.of(
                    "success", false,
                    "message", "Invalid webhook payload"));
        }

        PaymentTransaction transaction = paymentTransactionRepository.findByOrderInvoiceNumber(request.order().orderInvoiceNumber())
                .orElse(null);
        if (transaction == null) {
            return new WebhookProcessingResult(HttpStatus.OK, Map.of(
                    "success", true,
                    "ignored", true,
                    "message", "Payment transaction not found"));
        }

        String webhookEventKey = buildWebhookEventKey(request);
        if (isDuplicateWebhook(transaction, webhookEventKey)) {
            return new WebhookProcessingResult(HttpStatus.OK, Map.of(
                    "success", true,
                    "ignored", true,
                    "message", "Duplicate webhook ignored"));
        }

        if (transaction.getStatus() == PaymentTransactionStatus.EXPIRED) {
            markWebhookMeta(transaction, webhookEventKey);
            paymentTransactionRepository.save(transaction);
            return new WebhookProcessingResult(HttpStatus.OK, Map.of(
                    "success", true,
                    "ignored", true,
                    "message", "Payment session already expired"));
        }

        if ("ORDER_PAID".equalsIgnoreCase(request.notificationType())) {
            return handleOrderPaid(transaction, request, webhookEventKey);
        }

        if ("TRANSACTION_VOID".equalsIgnoreCase(request.notificationType())) {
            if (transaction.getStatus() == PaymentTransactionStatus.PAID) {
                markWebhookMeta(transaction, webhookEventKey);
                paymentTransactionRepository.save(transaction);
                return new WebhookProcessingResult(HttpStatus.OK, Map.of(
                        "success", true,
                        "ignored", true,
                        "message", "Paid transaction cannot be voided locally"));
            }
            transaction.setStatus(PaymentTransactionStatus.FAILED);
            transaction.setFailureReason("TRANSACTION_VOID");
            markWebhookMeta(transaction, webhookEventKey);
            paymentTransactionRepository.save(transaction);
            return new WebhookProcessingResult(HttpStatus.OK, Map.of(
                    "success", true,
                    "message", "Transaction marked as void"));
        }

        markWebhookMeta(transaction, webhookEventKey);
        paymentTransactionRepository.save(transaction);
        return new WebhookProcessingResult(HttpStatus.OK, Map.of(
                "success", true,
                "ignored", true,
                "message", "Unsupported notification type"));
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

    private WebhookProcessingResult handleOrderPaid(PaymentTransaction transaction,
                                                    SePayIpnRequest request,
                                                    String webhookEventKey) {
        if (transaction.getStatus() == PaymentTransactionStatus.PAID
                || transaction.getStatus() == PaymentTransactionStatus.REFUND_PENDING
                || transaction.getStatus() == PaymentTransactionStatus.REFUNDED) {
            markWebhookMeta(transaction, webhookEventKey);
            paymentTransactionRepository.save(transaction);
            return new WebhookProcessingResult(HttpStatus.OK, Map.of(
                    "success", true,
                    "ignored", true,
                    "message", "Transaction already processed"));
        }

        BigDecimal webhookAmount = parseAmount(request.order() != null ? request.order().orderAmount() : null);
        if (webhookAmount == null || transaction.getAmount() == null || transaction.getAmount().compareTo(webhookAmount) != 0) {
            transaction.setStatus(PaymentTransactionStatus.FAILED);
            transaction.setFailureReason("AMOUNT_MISMATCH");
            markWebhookMeta(transaction, webhookEventKey);
            paymentTransactionRepository.save(transaction);
            return new WebhookProcessingResult(HttpStatus.OK, Map.of(
                    "success", true,
                    "ignored", true,
                    "message", "Amount mismatch"));
        }

        if (transaction.getExpiresAt() != null && !transaction.getExpiresAt().isAfter(LocalDateTime.now())) {
            transaction.setStatus(PaymentTransactionStatus.EXPIRED);
            transaction.setExpiredAt(LocalDateTime.now());
            markWebhookMeta(transaction, webhookEventKey);
            paymentTransactionRepository.save(transaction);
            return new WebhookProcessingResult(HttpStatus.OK, Map.of(
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

        try {
            bookingGrpcClient.confirmBookingPayment(
                    transaction.getBookingId(),
                    transaction.getAmount(),
                    transaction.getPaymentMethod(),
                    transaction.getProviderRef(),
                    transaction.getOrderInvoiceNumber());
        } catch (BusinessException ex) {
            transaction.setFailureReason("BOOKING_CONFIRM_FAILED:" + ex.getErrorCode().name());
            paymentTransactionRepository.save(transaction);
            return new WebhookProcessingResult(HttpStatus.OK, Map.of(
                    "success", true,
                    "message", "Payment recorded but booking confirmation failed",
                    "booking_error", ex.getErrorCode().name()));
        }

        return new WebhookProcessingResult(HttpStatus.OK, Map.of(
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
            BookingGrpcClient.BookingPaymentContext context = bookingGrpcClient.getBookingPaymentContext(transaction.getBookingId());
            ensureRequesterOwnsBooking(requesterUserId, context.userId());
        }
    }

    private boolean isReusable(PaymentTransaction latest, BookingGrpcClient.BookingPaymentContext bookingContext) {
        if (latest.getStatus() == PaymentTransactionStatus.PENDING) {
            return latest.getExpiresAt() != null && latest.getExpiresAt().isAfter(LocalDateTime.now());
        }
        if (latest.getStatus() == PaymentTransactionStatus.PAID) {
            return true;
        }
        return latest.getStatus() == PaymentTransactionStatus.EXPIRED
                && bookingContext.reservedUntil() != null
                && bookingContext.reservedUntil().isAfter(LocalDateTime.now());
    }

    private PaymentSessionResponse toResponse(PaymentTransaction transaction) {
        return PaymentSessionResponse.builder()
                .id(transaction.getId())
                .bookingId(transaction.getBookingId())
                .showtimeId(transaction.getShowtimeId())
                .userId(transaction.getUserId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .paymentMethod(transaction.getPaymentMethod())
                .orderInvoiceNumber(transaction.getOrderInvoiceNumber())
                .providerRef(transaction.getProviderRef())
                .checkoutUrl(transaction.getCheckoutUrl())
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
                .promotionDiscountAmount(transaction.getPromotionDiscountAmount())
                .build();
    }

    private String writeJson(Map<String, String> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
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

    private BigDecimal parseAmount(String rawAmount) {
        if (!StringUtils.hasText(rawAmount)) {
            return null;
        }
        try {
            return new BigDecimal(rawAmount.trim()).setScale(0, RoundingMode.HALF_UP);
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

    private String extractProviderRef(SePayIpnRequest request) {
        if (request.order() != null && StringUtils.hasText(request.order().orderId())) {
            return request.order().orderId();
        }
        if (request.transaction() != null && StringUtils.hasText(request.transaction().transactionId())) {
            return request.transaction().transactionId();
        }
        return null;
    }

    private LocalDateTime parseTransactionDate(SePayIpnRequest request) {
        if (request.transaction() == null || !StringUtils.hasText(request.transaction().transactionDate())) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(request.transaction().transactionDate().replace(" ", "T"));
        } catch (Exception ex) {
            return LocalDateTime.now();
        }
    }

    private String buildWebhookEventKey(SePayIpnRequest request) {
        String notificationType = safeWebhookPart(request.notificationType());
        String invoice = safeWebhookPart(request.order() == null ? null : request.order().orderInvoiceNumber());
        String orderId = safeWebhookPart(request.order() == null ? null : request.order().orderId());
        String transactionId = safeWebhookPart(request.transaction() == null ? null : request.transaction().transactionId());
        return notificationType + "|" + invoice + "|" + orderId + "|" + transactionId;
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

    private String normalizePromotionCode(String code) {
        if (!StringUtils.hasText(code)) {
            return "";
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private PromotionCalculation calculatePromotion(BigDecimal baseAmount, String promotionCode) {
        if (!StringUtils.hasText(promotionCode)) {
            return new PromotionCalculation("", ZERO, "No promotion code");
        }

        if ("CINEMASTAR10".equals(promotionCode)) {
            if (baseAmount.compareTo(MIN_FOR_CINEMASTAR10) < 0) {
                return new PromotionCalculation(promotionCode, ZERO, "Min order is 100000 VND");
            }
            BigDecimal discount = normalizeAmount(baseAmount.multiply(TEN_PERCENT));
            if (discount.compareTo(THIRTY_THOUSAND) > 0) {
                discount = THIRTY_THOUSAND;
            }
            return new PromotionCalculation(promotionCode, discount, "Applied 10% discount (max 30000 VND)");
        }

        if ("COMBO20K".equals(promotionCode)) {
            if (baseAmount.compareTo(MIN_FOR_COMBO20K) < 0) {
                return new PromotionCalculation(promotionCode, ZERO, "Min order is 150000 VND");
            }
            return new PromotionCalculation(promotionCode, TWENTY_THOUSAND, "Applied fixed 20000 VND discount");
        }

        if ("WEEKDAY15".equals(promotionCode)) {
            BigDecimal discount = normalizeAmount(baseAmount.multiply(FIFTEEN_PERCENT));
            if (discount.compareTo(FORTY_THOUSAND) > 0) {
                discount = FORTY_THOUSAND;
            }
            return new PromotionCalculation(promotionCode, discount, "Applied 15% discount (max 40000 VND)");
        }

        return new PromotionCalculation(promotionCode, ZERO, "Promotion code is not supported");
    }

    private record PromotionCalculation(String code, BigDecimal discount, String note) {
    }
}
