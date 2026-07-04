package com.cinema.review_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.booking.BookingInternalServiceGrpc;
import com.cinema.grpc.booking.CheckUserEligibleForReviewReply;
import com.cinema.grpc.booking.CheckUserEligibleForReviewRequest;
import com.cinema.grpc.GrpcErrorUtils;
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

    public boolean isUserEligibleForReview(UUID userId, UUID filmId) {
        try {
            CheckUserEligibleForReviewReply reply = bookingBlockingStub.checkUserEligibleForReview(
                    CheckUserEligibleForReviewRequest.newBuilder()
                            .setUserId(userId.toString())
                            .setFilmId(filmId.toString())
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.BOOKING_SERVICE_ERROR));
            }

            return reply.getEligible();
        } catch (BusinessException ex) {
            throw ex;
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.BOOKING_SERVICE_ERROR);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.BOOKING_SERVICE_ERROR);
        }
    }
}
