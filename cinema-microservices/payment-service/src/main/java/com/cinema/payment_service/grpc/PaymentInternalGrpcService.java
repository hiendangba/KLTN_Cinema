package com.cinema.payment_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.payment.GetPaymentSessionByBookingIdReply;
import com.cinema.grpc.payment.GetPaymentSessionByBookingIdRequest;
import com.cinema.grpc.payment.PaymentInternalServiceGrpc;
import com.cinema.grpc.payment.PaymentSessionPayload;
import com.cinema.payment_service.dto.response.PaymentSessionResponse;
import com.cinema.payment_service.services.PaymentSessionService;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentInternalGrpcService extends PaymentInternalServiceGrpc.PaymentInternalServiceImplBase
        implements BindableService {

    private final PaymentSessionService paymentSessionService;

    @Override
    public void getPaymentSessionByBookingId(
            GetPaymentSessionByBookingIdRequest request,
            StreamObserver<GetPaymentSessionByBookingIdReply> responseObserver) {
        log.info(
                "PAYMENT_SESSION_GRPC_REQUEST bookingId={} requesterUserId={} requesterRole={}",
                request.getBookingId(),
                request.getRequesterUserId(),
                request.getRequesterRole());
        UUID bookingId;
        UUID requesterUserId;
        try {
            bookingId = UUID.fromString(request.getBookingId());
            requesterUserId = UUID.fromString(request.getRequesterUserId());
        } catch (IllegalArgumentException ex) {
            log.warn(
                    "PAYMENT_SESSION_GRPC_INVALID_FORMAT bookingId={} requesterUserId={} requesterRole={}",
                    request.getBookingId(),
                    request.getRequesterUserId(),
                    request.getRequesterRole());
            responseObserver.onNext(errorReply(ErrorCode.INVALID_FORMAT));
            responseObserver.onCompleted();
            return;
        }

        try {
            PaymentSessionResponse session = paymentSessionService.getSession(
                    bookingId,
                    requesterUserId,
                    request.getRequesterRole());
            log.info(
                    "PAYMENT_SESSION_GRPC_SUCCESS bookingId={} requesterUserId={} requesterRole={} sessionId={} status={}",
                    bookingId,
                    requesterUserId,
                    request.getRequesterRole(),
                    session == null ? null : session.getId(),
                    session == null || session.getStatus() == null ? null : session.getStatus().name());
            responseObserver.onNext(GetPaymentSessionByBookingIdReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("Payment session fetched successfully")
                    .setSession(toPayload(session))
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            log.warn(
                    "PAYMENT_SESSION_GRPC_BUSINESS_ERROR bookingId={} requesterUserId={} requesterRole={} errorCode={} message={}",
                    bookingId,
                    requesterUserId,
                    request.getRequesterRole(),
                    ex.getErrorCode().name(),
                    ex.getMessage());
            responseObserver.onNext(errorReply(ex.getErrorCode()));
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error(
                    "PAYMENT_SESSION_GRPC_UNEXPECTED_ERROR bookingId={} requesterUserId={} requesterRole={}",
                    bookingId,
                    requesterUserId,
                    request.getRequesterRole(),
                    ex);
            responseObserver.onNext(errorReply(ErrorCode.INTERNAL_ERROR));
            responseObserver.onCompleted();
        }
    }

    private GetPaymentSessionByBookingIdReply errorReply(ErrorCode errorCode) {
        return GetPaymentSessionByBookingIdReply.newBuilder()
                .setSuccess(false)
                .setErrorKey(errorCode.name())
                .setMessage(errorCode.getMessage())
                .build();
    }

    private PaymentSessionPayload toPayload(PaymentSessionResponse session) {
        return PaymentSessionPayload.newBuilder()
                .setId(toText(session == null ? null : session.getId()))
                .setBookingId(toText(session == null ? null : session.getBookingId()))
                .setShowtimeId(toText(session == null ? null : session.getShowtimeId()))
                .setUserId(toText(session == null ? null : session.getUserId()))
                .setPaymentMethod(blankToEmpty(session == null ? null : session.getPaymentMethod()))
                .setOrderInvoiceNumber(blankToEmpty(session == null ? null : session.getOrderInvoiceNumber()))
                .setProviderRef(blankToEmpty(session == null ? null : session.getProviderRef()))
                .setPayUrl(blankToEmpty(session == null ? null : session.getPayUrl()))
                .setStatus(session == null || session.getStatus() == null ? "" : session.getStatus().name())
                .setAmount(toAmount(session == null ? null : session.getAmount()))
                .setPromotionCode(blankToEmpty(session == null ? null : session.getPromotionCode()))
                .setPromotionName(blankToEmpty(session == null ? null : session.getPromotionName()))
                .setPromotionDiscountAmount(toAmount(session == null ? null : session.getPromotionDiscountAmount()))
                .setLoyaltyPointsUsed(normalizePoints(session == null ? null : session.getLoyaltyPointsUsed()))
                .setLoyaltyPointsEarned(normalizePoints(session == null ? null : session.getLoyaltyPointsEarned()))
                .setExpiresAt(toDateTime(session == null ? null : session.getExpiresAt()))
                .setPaidAt(toDateTime(session == null ? null : session.getPaidAt()))
                .setExpiredAt(toDateTime(session == null ? null : session.getExpiredAt()))
                .setFailureReason(blankToEmpty(session == null ? null : session.getFailureReason()))
                .build();
    }

    private String toText(UUID value) {
        return value == null ? "" : value.toString();
    }

    private String toAmount(BigDecimal amount) {
        return amount == null ? "0" : amount.toPlainString();
    }

    private String toDateTime(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private long normalizePoints(Long points) {
        return points == null || points < 0 ? 0L : points;
    }
}
