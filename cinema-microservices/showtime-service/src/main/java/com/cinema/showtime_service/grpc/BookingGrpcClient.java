package com.cinema.showtime_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.booking.BookingInternalServiceGrpc;
import com.cinema.grpc.booking.CheckShowtimeBookedReply;
import com.cinema.grpc.booking.CheckShowtimeBookedRequest;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class BookingGrpcClient {

    private final BookingInternalServiceGrpc.BookingInternalServiceBlockingStub bookingBlockingStub;

    public BookingGrpcClient(GrpcChannelFactory channelFactory) {
        this.bookingBlockingStub = BookingInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("booking"));
    }

    public boolean isShowtimeBooked(UUID showtimeId) {
        try {
            CheckShowtimeBookedReply reply = bookingBlockingStub.checkShowtimeBooked(
                    CheckShowtimeBookedRequest.newBuilder()
                            .setShowtimeId(showtimeId.toString())
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.BOOKING_SERVICE_ERROR));
            }

            return reply.getBooked();
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.BOOKING_SERVICE_ERROR);
        }
    }
}
