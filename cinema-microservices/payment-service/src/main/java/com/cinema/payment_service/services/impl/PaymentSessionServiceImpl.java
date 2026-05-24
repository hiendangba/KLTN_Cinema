package com.cinema.payment_service.services.impl;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.payment_service.config.SePayGatewayProperties;
import com.cinema.payment_service.dto.request.CreatePaymentSessionRequest;
import com.cinema.payment_service.dto.response.PaymentSessionResponse;
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
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentSessionServiceImpl implements PaymentSessionService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final BookingGrpcClient bookingGrpcClient;
    private final SePayGatewayProperties sePayGatewayProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public PaymentSessionResponse createSession(CreatePaymentSessionRequest request) {
        if (request == null || request.getBookingId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        UUID bookingId = request.getBookingId();
        BookingGrpcClient.BookingPaymentContext bookingContext = bookingGrpcClient.getBookingPaymentContext(bookingId);
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
        if (latest != null && isReusable(latest, bookingContext)) {
            return toResponse(latest);
        }

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setBookingId(bookingContext.bookingId());
        transaction.setShowtimeId(bookingContext.showtimeId());
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
    public PaymentSessionResponse getSession(UUID bookingId) {
        if (bookingId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        PaymentTransaction transaction = paymentTransactionRepository.findFirstByBookingIdOrderByTimeCreatedDesc(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return toResponse(transaction);
    }

    @Override
    @Transactional
    public WebhookProcessingResult handleSePayWebhook(String secretKey, SePayIpnRequest request) {
        if (!StringUtils.hasText(secretKey) || !secretKey.equals(sePayGatewayProperties.getSecretKey())) {
            return new WebhookProcessingResult(HttpStatus.UNAUTHORIZED, Map.<String, Object>of(
                    "success", false,
                    "message", "Unauthorized"));
        }
        if (request == null || request.order() == null || !StringUtils.hasText(request.order().orderInvoiceNumber())) {
            return new WebhookProcessingResult(HttpStatus.BAD_REQUEST, Map.<String, Object>of(
                    "success", false,
                    "message", "Invalid webhook payload"));
        }

        PaymentTransaction transaction = paymentTransactionRepository.findByOrderInvoiceNumber(request.order().orderInvoiceNumber())
                .orElse(null);
        if (transaction == null) {
            return new WebhookProcessingResult(HttpStatus.OK, Map.<String, Object>of(
                    "success", true,
                    "ignored", true,
                    "message", "Payment transaction not found"));
        }

        if (transaction.getStatus() == PaymentTransactionStatus.EXPIRED) {
            return new WebhookProcessingResult(HttpStatus.OK, Map.<String, Object>of(
                    "success", true,
                    "ignored", true,
                    "message", "Payment session already expired"));
        }

        if ("ORDER_PAID".equalsIgnoreCase(request.notificationType())) {
            return handleOrderPaid(transaction, request);
        }

        if ("TRANSACTION_VOID".equalsIgnoreCase(request.notificationType())) {
            if (transaction.getStatus() == PaymentTransactionStatus.PAID) {
                return new WebhookProcessingResult(HttpStatus.OK, Map.<String, Object>of(
                        "success", true,
                        "ignored", true,
                        "message", "Paid transaction cannot be voided locally"));
            }
            transaction.setStatus(PaymentTransactionStatus.FAILED);
            transaction.setFailureReason("TRANSACTION_VOID");
            paymentTransactionRepository.save(transaction);
            return new WebhookProcessingResult(HttpStatus.OK, Map.<String, Object>of(
                    "success", true,
                    "message", "Transaction marked as void"));
        }

        return new WebhookProcessingResult(HttpStatus.OK, Map.<String, Object>of(
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

    private WebhookProcessingResult handleOrderPaid(PaymentTransaction transaction, SePayIpnRequest request) {
        if (transaction.getStatus() == PaymentTransactionStatus.PAID) {
            return new WebhookProcessingResult(HttpStatus.OK, Map.<String, Object>of(
                    "success", true,
                    "ignored", true,
                    "message", "Transaction already processed"));
        }

        BigDecimal webhookAmount = parseAmount(request.order() != null ? request.order().orderAmount() : null);
        if (webhookAmount == null || transaction.getAmount() == null || transaction.getAmount().compareTo(webhookAmount) != 0) {
            transaction.setStatus(PaymentTransactionStatus.FAILED);
            transaction.setFailureReason("AMOUNT_MISMATCH");
            paymentTransactionRepository.save(transaction);
            return new WebhookProcessingResult(HttpStatus.OK, Map.<String, Object>of(
                    "success", true,
                    "ignored", true,
                    "message", "Amount mismatch"));
        }

        if (transaction.getExpiresAt() != null && !transaction.getExpiresAt().isAfter(LocalDateTime.now())) {
            transaction.setStatus(PaymentTransactionStatus.EXPIRED);
            transaction.setExpiredAt(LocalDateTime.now());
            paymentTransactionRepository.save(transaction);
            return new WebhookProcessingResult(HttpStatus.OK, Map.<String, Object>of(
                    "success", true,
                    "ignored", true,
                    "message", "Payment session expired"));
        }

        transaction.setStatus(PaymentTransactionStatus.PAID);
        transaction.setPaidAt(parseTransactionDate(request));
        transaction.setProviderRef(extractProviderRef(request));
        transaction.setFailureReason(null);
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
            return new WebhookProcessingResult(HttpStatus.OK, Map.<String, Object>of(
                    "success", true,
                    "message", "Payment recorded but booking confirmation failed",
                    "booking_error", ex.getErrorCode().name()));
        }

        return new WebhookProcessingResult(HttpStatus.OK, Map.<String, Object>of(
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
        if ("PAID".equalsIgnoreCase(bookingContext.paymentStatus())) {
            return;
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
            return Map.<String, String>of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (Exception ex) {
            return Map.<String, String>of();
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
            return BigDecimal.ZERO.setScale(0, RoundingMode.HALF_UP);
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
}
