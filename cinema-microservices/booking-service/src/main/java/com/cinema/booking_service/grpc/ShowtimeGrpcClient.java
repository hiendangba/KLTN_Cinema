package com.cinema.booking_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.showtime.GetShowtimeByIdReply;
import com.cinema.grpc.showtime.GetShowtimeByIdRequest;
import com.cinema.grpc.showtime.ShowtimeInternalServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.Builder;
import lombok.Getter;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class ShowtimeGrpcClient {

    private final ShowtimeInternalServiceGrpc.ShowtimeInternalServiceBlockingStub showtimeBlockingStub;

    public ShowtimeGrpcClient(GrpcChannelFactory channelFactory) {
        this.showtimeBlockingStub = ShowtimeInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("showtime"));
    }

    public ShowtimeSummary getShowtimeById(UUID showtimeId) {
        try {
            GetShowtimeByIdReply reply = showtimeBlockingStub.getShowtimeById(
                    GetShowtimeByIdRequest.newBuilder()
                            .setShowtimeId(showtimeId.toString())
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(
                        GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.SHOWTIME_SERVICE_ERROR));
            }

            if (!reply.hasShowtime()) {
                throw new BusinessException(ErrorCode.SHOWTIME_NOT_FOUND);
            }

            return ShowtimeSummary.builder()
                    .id(parseUuid(reply.getShowtime().getId(), ErrorCode.SHOWTIME_SERVICE_ERROR))
                    .hallId(parseUuid(reply.getShowtime().getHallId(), ErrorCode.SHOWTIME_SERVICE_ERROR))
                    .cinemaId(parseUuid(reply.getShowtime().getCinemaId(), ErrorCode.SHOWTIME_SERVICE_ERROR))
                    .pricingPolicyId(parseUuid(reply.getShowtime().getPricingPolicyId(), ErrorCode.SHOWTIME_SERVICE_ERROR))
                    .filmId(parseUuid(reply.getShowtime().getFilmId(), ErrorCode.SHOWTIME_SERVICE_ERROR))
                    .startDateTime(parseDateTime(reply.getShowtime().getStartDateTime(), ErrorCode.SHOWTIME_SERVICE_ERROR))
                    .endDateTime(parseDateTime(reply.getShowtime().getEndDateTime(), ErrorCode.SHOWTIME_SERVICE_ERROR))
                    .build();
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.SHOWTIME_SERVICE_ERROR);
        }
    }

    private UUID parseUuid(String raw, ErrorCode fallback) {
        try {
            return UUID.fromString(raw);
        } catch (Exception ex) {
            throw new BusinessException(fallback);
        }
    }

    private LocalDateTime parseDateTime(String raw, ErrorCode fallback) {
        try {
            return LocalDateTime.parse(raw);
        } catch (Exception ex) {
            throw new BusinessException(fallback);
        }
    }

    @Getter
    @Builder
    public static class ShowtimeSummary {
        private UUID id;
        private UUID hallId;
        private UUID cinemaId;
        private UUID pricingPolicyId;
        private UUID filmId;
        private LocalDateTime startDateTime;
        private LocalDateTime endDateTime;
    }
}
