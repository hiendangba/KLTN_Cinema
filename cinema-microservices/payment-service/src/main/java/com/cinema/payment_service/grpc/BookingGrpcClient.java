package com.cinema.payment_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.booking.BookingInternalServiceGrpc;
import com.cinema.grpc.booking.BookingPaymentContextPayload;
import com.cinema.grpc.booking.ConfirmBookingPaymentReply;
import com.cinema.grpc.booking.ConfirmBookingPaymentRequest;
import com.cinema.grpc.booking.GetBookingPaymentContextReply;
import com.cinema.grpc.booking.GetBookingPaymentContextRequest;
import com.cinema.grpc.booking.UpsertBookingPromotionSnapshotReply;
import com.cinema.grpc.booking.UpsertBookingPromotionSnapshotRequest;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Slf4j
public class BookingGrpcClient {

    private final BookingInternalServiceGrpc.BookingInternalServiceBlockingStub bookingBlockingStub;

    public BookingGrpcClient(GrpcChannelFactory channelFactory) {
        this.bookingBlockingStub = BookingInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("booking"));
    }

    public BookingPaymentContext getBookingPaymentContext(UUID bookingId) {
        try {
            log.info("BOOKING_CONTEXT_REQUEST bookingId={}", bookingId);
            GetBookingPaymentContextReply reply = bookingBlockingStub.getBookingPaymentContext(
                    GetBookingPaymentContextRequest.newBuilder()
                            .setBookingId(bookingId.toString())
                            .build());

            if (!reply.getSuccess()) {
                log.warn("BOOKING_CONTEXT_REPLY_FAILED bookingId={} errorKey={} message={}",
                        bookingId,
                        reply.getErrorKey(),
                        reply.getMessage());
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.BOOKING_SERVICE_ERROR));
            }

            log.info("BOOKING_CONTEXT_REPLY_OK bookingId={} payloadBookingId={}",
                    bookingId,
                    reply.getBooking() == null ? "" : reply.getBooking().getBookingId());
            return toContext(reply.getBooking());
        } catch (StatusRuntimeException ex) {
            log.error("BOOKING_CONTEXT_GRPC_FAILURE bookingId={} status={} description={}",
                    bookingId,
                    ex.getStatus(),
                    ex.getStatus() == null ? "" : ex.getStatus().getDescription(),
                    ex);
            throw new BusinessException(ErrorCode.BOOKING_SERVICE_ERROR);
        }
    }

    public BookingPaymentConfirmation confirmBookingPayment(UUID bookingId,
                                                            BigDecimal amount,
                                                            String paymentMethod,
                                                            String providerRef,
                                                            String orderInvoiceNumber,
                                                            Long loyaltyPointsUsed,
                                                            Long loyaltyPointsEarned) {
        try {
            ConfirmBookingPaymentReply reply = bookingBlockingStub.confirmBookingPayment(
                    ConfirmBookingPaymentRequest.newBuilder()
                            .setBookingId(bookingId.toString())
                            .setTransactionAmount(amount == null ? "0" : amount.toPlainString())
                            .setPaymentMethod(paymentMethod == null ? "" : paymentMethod)
                            .setProviderRef(providerRef == null ? "" : providerRef)
                            .setOrderInvoiceNumber(orderInvoiceNumber == null ? "" : orderInvoiceNumber)
                            .setLoyaltyPointsUsed(normalizePoints(loyaltyPointsUsed))
                            .setLoyaltyPointsEarned(normalizePoints(loyaltyPointsEarned))
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.BOOKING_SERVICE_ERROR));
            }

            return new BookingPaymentConfirmation(toContext(reply.getBooking()));
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.BOOKING_SERVICE_ERROR);
        }
    }

    public BookingPaymentContext upsertBookingPromotionSnapshot(UUID bookingId,
                                                                UUID promotionId,
                                                                String promotionCode,
                                                                String promotionName,
                                                                BigDecimal promotionDiscountAmount,
                                                                BigDecimal payableAmount,
                                                                Long loyaltyPointsUsed,
                                                                Long loyaltyPointsEarned) {
        try {
            UpsertBookingPromotionSnapshotReply reply = bookingBlockingStub.upsertBookingPromotionSnapshot(
                    UpsertBookingPromotionSnapshotRequest.newBuilder()
                            .setBookingId(bookingId == null ? "" : bookingId.toString())
                            .setPromotionId(promotionId == null ? "" : promotionId.toString())
                            .setPromotionCode(promotionCode == null ? "" : promotionCode)
                            .setPromotionName(promotionName == null ? "" : promotionName)
                            .setPromotionDiscountAmount(promotionDiscountAmount == null ? "0" : promotionDiscountAmount.toPlainString())
                            .setPayableAmount(payableAmount == null ? "0" : payableAmount.toPlainString())
                            .setLoyaltyPointsUsed(normalizePoints(loyaltyPointsUsed))
                            .setLoyaltyPointsEarned(normalizePoints(loyaltyPointsEarned))
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.BOOKING_SERVICE_ERROR));
            }

            return toContext(reply.getBooking());
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.BOOKING_SERVICE_ERROR);
        }
    }

    private BookingPaymentContext toContext(BookingPaymentContextPayload payload) {
        if (payload == null || payload.getBookingId().isBlank()) {
            log.error("BOOKING_CONTEXT_INVALID_PAYLOAD payloadBookingId={} payloadShowtimeId={} payloadCinemaId={} payloadUserId={}",
                    payload == null ? "" : payload.getBookingId(),
                    payload == null ? "" : payload.getShowtimeId(),
                    payload == null ? "" : payload.getCinemaId(),
                    payload == null ? "" : payload.getUserId());
            throw new BusinessException(ErrorCode.BOOKING_SERVICE_ERROR);
        }

        try {
            return new BookingPaymentContext(
                    UUID.fromString(payload.getBookingId()),
                    UUID.fromString(payload.getShowtimeId()),
                    UUID.fromString(payload.getCinemaId()),
                    parseNullableUuid(payload.getFilmId()),
                    UUID.fromString(payload.getUserId()),
                    new BigDecimal(payload.getFinalAmount()),
                    payload.getReservedUntil().isBlank() ? null : LocalDateTime.parse(payload.getReservedUntil()),
                    payload.getBookingStatus(),
                    payload.getPaymentStatus(),
                    parseAmount(payload.getTicketSubtotal()),
                    parseAmount(payload.getProductSubtotal()),
                    parseNullableUuid(payload.getPromotionId()),
                    blankToNull(payload.getPromotionCode()),
                    blankToNull(payload.getPromotionName()),
                    parseAmount(payload.getPromotionDiscountAmount()),
                    parseAmount(payload.getPayableAmount()),
                    normalizePoints(payload.getLoyaltyPointsUsed()),
                    normalizePoints(payload.getLoyaltyPointsEarned()));
        } catch (Exception ex) {
            log.error(
                    "BOOKING_CONTEXT_PARSE_FAILED payloadBookingId={} payloadShowtimeId={} payloadCinemaId={} payloadFilmId={} payloadUserId={} payloadReservedUntil={} payloadFinalAmount={} payloadBookingStatus={} payloadPaymentStatus={}",
                    payload.getBookingId(),
                    payload.getShowtimeId(),
                    payload.getCinemaId(),
                    payload.getFilmId(),
                    payload.getUserId(),
                    payload.getReservedUntil(),
                    payload.getFinalAmount(),
                    payload.getBookingStatus(),
                    payload.getPaymentStatus(),
                    ex);
            throw new BusinessException(ErrorCode.BOOKING_SERVICE_ERROR);
        }
    }

    private BigDecimal parseAmount(String rawAmount) {
        try {
            return rawAmount == null || rawAmount.isBlank() ? BigDecimal.ZERO : new BigDecimal(rawAmount);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BOOKING_SERVICE_ERROR);
        }
    }

    private UUID parseNullableUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BOOKING_SERVICE_ERROR);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private long normalizePoints(Long points) {
        return points == null || points < 0 ? 0L : points;
    }

    private long normalizePoints(long points) {
        return Math.max(0L, points);
    }

    public record BookingPaymentContext(
            UUID bookingId,
            UUID showtimeId,
            UUID cinemaId,
            UUID filmId,
            UUID userId,
            BigDecimal finalAmount,
            LocalDateTime reservedUntil,
            String bookingStatus,
            String paymentStatus,
            BigDecimal ticketSubtotal,
            BigDecimal productSubtotal,
            UUID promotionId,
            String promotionCode,
            String promotionName,
            BigDecimal promotionDiscountAmount,
            BigDecimal payableAmount,
            long loyaltyPointsUsed,
            long loyaltyPointsEarned) {
    }

    public record BookingPaymentConfirmation(BookingPaymentContext booking) {
    }
}
