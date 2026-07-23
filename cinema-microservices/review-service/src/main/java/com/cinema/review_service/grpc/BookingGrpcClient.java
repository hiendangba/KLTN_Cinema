package com.cinema.review_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.booking.BookingInternalServiceGrpc;
import com.cinema.grpc.booking.CheckUserEligibleForReviewReply;
import com.cinema.grpc.booking.CheckUserEligibleForReviewRequest;
import com.cinema.grpc.GrpcErrorUtils;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
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
                ErrorCode resolvedError = GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.BOOKING_SERVICE_ERROR);
                log.warn(
                        "Booking review eligibility rejected userId={} filmId={} errorKey={} resolvedCode={} message={}",
                        userId,
                        filmId,
                        reply.getErrorKey(),
                        resolvedError.name(),
                        reply.getMessage());
                throw new BusinessException(resolvedError);
            }

            return reply.getEligible();
        } catch (BusinessException ex) {
            log.warn(
                    "Booking review eligibility business exception userId={} filmId={} code={} message={}",
                    userId,
                    filmId,
                    ex.getErrorCode().name(),
                    ex.getMessage());
            throw ex;
        } catch (StatusRuntimeException ex) {
            log.error(
                    "Booking review eligibility gRPC transport failure userId={} filmId={} status={} description={}",
                    userId,
                    filmId,
                    ex.getStatus().getCode(),
                    ex.getStatus().getDescription(),
                    ex);
            throw new BusinessException(ErrorCode.BOOKING_SERVICE_ERROR);
        } catch (RuntimeException ex) {
            log.error("Booking review eligibility unexpected runtime failure userId={} filmId={}", userId, filmId, ex);
            throw new BusinessException(ErrorCode.BOOKING_SERVICE_ERROR);
        }
    }
}
