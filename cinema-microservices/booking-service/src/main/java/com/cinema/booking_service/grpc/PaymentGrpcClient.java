package com.cinema.booking_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.payment.GetPaymentSessionByBookingIdReply;
import com.cinema.grpc.payment.GetPaymentSessionByBookingIdRequest;
import com.cinema.grpc.payment.PaymentInternalServiceGrpc;
import com.cinema.grpc.payment.PaymentSessionPayload;
import com.cinema.booking_service.dto.response.PaymentSessionSnapshotResponse;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Slf4j
public class PaymentGrpcClient {

    private final PaymentInternalServiceGrpc.PaymentInternalServiceBlockingStub paymentBlockingStub;

    public PaymentGrpcClient(GrpcChannelFactory channelFactory) {
        this.paymentBlockingStub = PaymentInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("payment"));
    }

    public PaymentSessionSnapshotResponse getSessionByBookingId(UUID bookingId, UUID requesterUserId, String requesterRole) {
        log.info(
                "CHECKOUT_CONTEXT_PAYMENT_GRPC_REQUEST bookingId={} requesterUserId={} requesterRole={}",
                bookingId,
                requesterUserId,
                requesterRole);
        try {
            GetPaymentSessionByBookingIdReply reply = paymentBlockingStub.getPaymentSessionByBookingId(
                    GetPaymentSessionByBookingIdRequest.newBuilder()
                            .setBookingId(bookingId == null ? "" : bookingId.toString())
                            .setRequesterUserId(requesterUserId == null ? "" : requesterUserId.toString())
                            .setRequesterRole(requesterRole == null ? "" : requesterRole)
                            .build());

            if (!reply.getSuccess()) {
                log.warn(
                        "CHECKOUT_CONTEXT_PAYMENT_GRPC_REPLY_FAILED bookingId={} requesterUserId={} requesterRole={} errorKey={} message={}",
                        bookingId,
                        requesterUserId,
                        requesterRole,
                        reply.getErrorKey(),
                        reply.getMessage());
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.EXTERNAL_SERVICE_ERROR));
            }

            PaymentSessionSnapshotResponse snapshot = toSnapshot(reply.getSession());
            log.info(
                    "CHECKOUT_CONTEXT_PAYMENT_GRPC_REPLY_OK bookingId={} requesterUserId={} requesterRole={} sessionId={} status={}",
                    bookingId,
                    requesterUserId,
                    requesterRole,
                    snapshot.getId(),
                    snapshot.getStatus());
            return snapshot;
        } catch (StatusRuntimeException ex) {
            log.error(
                    "CHECKOUT_CONTEXT_PAYMENT_GRPC_TRANSPORT_FAILED bookingId={} requesterUserId={} requesterRole={}",
                    bookingId,
                    requesterUserId,
                    requesterRole,
                    ex);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    private PaymentSessionSnapshotResponse toSnapshot(PaymentSessionPayload payload) {
        if (payload == null || payload.getBookingId().isBlank()) {
            log.warn(
                    "CHECKOUT_CONTEXT_PAYMENT_GRPC_INVALID_PAYLOAD bookingId={} reason=blank_booking_id",
                    payload == null ? null : payload.getBookingId());
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
        try {
            PaymentSessionSnapshotResponse response = new PaymentSessionSnapshotResponse();
            response.setId(parseNullableUuid(payload.getId()));
            response.setBookingId(UUID.fromString(payload.getBookingId()));
            response.setShowtimeId(parseNullableUuid(payload.getShowtimeId()));
            response.setUserId(parseNullableUuid(payload.getUserId()));
            response.setPaymentMethod(blankToNull(payload.getPaymentMethod()));
            response.setOrderInvoiceNumber(blankToNull(payload.getOrderInvoiceNumber()));
            response.setProviderRef(blankToNull(payload.getProviderRef()));
            response.setPayUrl(blankToNull(payload.getPayUrl()));
            response.setStatus(blankToNull(payload.getStatus()));
            response.setAmount(parseAmount(payload.getAmount()));
            response.setPromotionCode(blankToNull(payload.getPromotionCode()));
            response.setPromotionName(blankToNull(payload.getPromotionName()));
            response.setPromotionDiscountAmount(parseAmount(payload.getPromotionDiscountAmount()));
            response.setLoyaltyPointsUsed(normalizePoints(payload.getLoyaltyPointsUsed()));
            response.setLoyaltyPointsEarned(normalizePoints(payload.getLoyaltyPointsEarned()));
            response.setExpiresAt(parseDateTime(payload.getExpiresAt()));
            response.setPaidAt(parseDateTime(payload.getPaidAt()));
            response.setExpiredAt(parseDateTime(payload.getExpiredAt()));
            response.setFailureReason(blankToNull(payload.getFailureReason()));
            return response;
        } catch (Exception ex) {
            log.error(
                    "CHECKOUT_CONTEXT_PAYMENT_GRPC_PARSE_FAILED bookingId={} payloadStatus={} payloadId={} payloadUserId={}",
                    payload.getBookingId(),
                    payload.getStatus(),
                    payload.getId(),
                    payload.getUserId(),
                    ex);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    private UUID parseNullableUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    private BigDecimal parseAmount(String value) {
        return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
    }

    private LocalDateTime parseDateTime(String value) {
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private long normalizePoints(long value) {
        return Math.max(0L, value);
    }
}
