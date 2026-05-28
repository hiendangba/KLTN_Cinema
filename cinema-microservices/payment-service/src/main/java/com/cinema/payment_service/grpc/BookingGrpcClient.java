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
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class BookingGrpcClient {

    private final BookingInternalServiceGrpc.BookingInternalServiceBlockingStub bookingBlockingStub;

    public BookingGrpcClient(GrpcChannelFactory channelFactory) {
        this.bookingBlockingStub = BookingInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("booking"));
    }

    public BookingPaymentContext getBookingPaymentContext(UUID bookingId) {
        try {
            GetBookingPaymentContextReply reply = bookingBlockingStub.getBookingPaymentContext(
                    GetBookingPaymentContextRequest.newBuilder()
                            .setBookingId(bookingId.toString())
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.BOOKING_SERVICE_ERROR));
            }

            return toContext(reply.getBooking());
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.BOOKING_SERVICE_ERROR);
        }
    }

    public BookingPaymentConfirmation confirmBookingPayment(UUID bookingId,
                                                            BigDecimal amount,
                                                            String paymentMethod,
                                                            String providerRef,
                                                            String orderInvoiceNumber) {
        try {
            ConfirmBookingPaymentReply reply = bookingBlockingStub.confirmBookingPayment(
                    ConfirmBookingPaymentRequest.newBuilder()
                            .setBookingId(bookingId.toString())
                            .setTransactionAmount(amount == null ? "0" : amount.toPlainString())
                            .setPaymentMethod(paymentMethod == null ? "" : paymentMethod)
                            .setProviderRef(providerRef == null ? "" : providerRef)
                            .setOrderInvoiceNumber(orderInvoiceNumber == null ? "" : orderInvoiceNumber)
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.BOOKING_SERVICE_ERROR));
            }

            return new BookingPaymentConfirmation(toContext(reply.getBooking()));
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.BOOKING_SERVICE_ERROR);
        }
    }

    private BookingPaymentContext toContext(BookingPaymentContextPayload payload) {
        if (payload == null || payload.getBookingId().isBlank()) {
            throw new BusinessException(ErrorCode.BOOKING_SERVICE_ERROR);
        }

        try {
            return new BookingPaymentContext(
                    UUID.fromString(payload.getBookingId()),
                    UUID.fromString(payload.getShowtimeId()),
                    UUID.fromString(payload.getCinemaId()),
                    UUID.fromString(payload.getUserId()),
                    new BigDecimal(payload.getFinalAmount()),
                    payload.getReservedUntil().isBlank() ? null : LocalDateTime.parse(payload.getReservedUntil()),
                    payload.getBookingStatus(),
                    payload.getPaymentStatus(),
                    parseAmount(payload.getTicketSubtotal()),
                    parseAmount(payload.getProductSubtotal()));
        } catch (Exception ex) {
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

    public record BookingPaymentContext(
            UUID bookingId,
            UUID showtimeId,
            UUID cinemaId,
            UUID userId,
            BigDecimal finalAmount,
            LocalDateTime reservedUntil,
            String bookingStatus,
            String paymentStatus,
            BigDecimal ticketSubtotal,
            BigDecimal productSubtotal) {
    }

    public record BookingPaymentConfirmation(BookingPaymentContext booking) {
    }
}
