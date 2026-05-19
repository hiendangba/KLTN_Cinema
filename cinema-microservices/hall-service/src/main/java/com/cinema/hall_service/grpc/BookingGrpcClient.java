package com.cinema.hall_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.booking.BookingInternalServiceGrpc;
import com.cinema.grpc.booking.HasActiveBookingByShowtimeIdsReply;
import com.cinema.grpc.booking.HasActiveBookingByShowtimeIdsRequest;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

@Component
public class BookingGrpcClient {

    private final BookingInternalServiceGrpc.BookingInternalServiceBlockingStub bookingBlockingStub;

    public BookingGrpcClient(GrpcChannelFactory channelFactory) {
        this.bookingBlockingStub = BookingInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("booking"));
    }

    public boolean hasActiveBookingByShowtimeIds(Collection<UUID> showtimeIds) {
        try {
            HasActiveBookingByShowtimeIdsReply reply = bookingBlockingStub.hasActiveBookingByShowtimeIds(
                    HasActiveBookingByShowtimeIdsRequest.newBuilder()
                            .addAllShowtimeIds(showtimeIds.stream().map(UUID::toString).toList())
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.BOOKING_SERVICE_ERROR));
            }
            return reply.getHasActiveBooking();
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.BOOKING_SERVICE_ERROR);
        }
    }
}
