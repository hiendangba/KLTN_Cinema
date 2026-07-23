package com.cinema.film_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.showtime.ListActiveFilmIdsReply;
import com.cinema.grpc.showtime.ListActiveFilmIdsRequest;
import com.cinema.grpc.showtime.ListActiveFilmIdsByCinemaReply;
import com.cinema.grpc.showtime.ListActiveFilmIdsByCinemaRequest;
import com.cinema.grpc.showtime.ListActiveFilmIdsByShowtimeDateReply;
import com.cinema.grpc.showtime.ListActiveFilmIdsByShowtimeDateRequest;
import com.cinema.grpc.showtime.ShowtimeInternalServiceGrpc;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Component
public class ShowtimeGrpcClient {

    private final ShowtimeInternalServiceGrpc.ShowtimeInternalServiceBlockingStub showtimeBlockingStub;

    public ShowtimeGrpcClient(GrpcChannelFactory channelFactory) {
        this.showtimeBlockingStub = ShowtimeInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("showtime"));
    }

    public Set<UUID> getActiveFilmIds() {
        try {
            ListActiveFilmIdsReply reply = showtimeBlockingStub.listActiveFilmIds(
                    ListActiveFilmIdsRequest.newBuilder().build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.SHOWTIME_SERVICE_ERROR));
            }

            Set<UUID> filmIds = new LinkedHashSet<>();
            reply.getFilmIdsList().stream()
                    .filter(filmId -> filmId != null && !filmId.isBlank())
                    .forEach(filmId -> filmIds.add(UUID.fromString(filmId)));
            return filmIds;
        } catch (BusinessException ex) {
            throw ex;
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.SHOWTIME_SERVICE_ERROR);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.SHOWTIME_SERVICE_ERROR);
        }
    }

    public Set<UUID> getActiveFilmIdsByCinema(UUID cinemaId) {
        try {
            ListActiveFilmIdsByCinemaReply reply = showtimeBlockingStub.listActiveFilmIdsByCinema(
                    ListActiveFilmIdsByCinemaRequest.newBuilder()
                            .setCinemaId(cinemaId.toString())
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.SHOWTIME_SERVICE_ERROR));
            }

            Set<UUID> filmIds = new LinkedHashSet<>();
            reply.getFilmIdsList().stream()
                    .filter(filmId -> filmId != null && !filmId.isBlank())
                    .forEach(filmId -> filmIds.add(UUID.fromString(filmId)));
            return filmIds;
        } catch (BusinessException ex) {
            throw ex;
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.SHOWTIME_SERVICE_ERROR);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.SHOWTIME_SERVICE_ERROR);
        }
    }

    public Set<UUID> getActiveFilmIdsByShowtimeDate(LocalDate showtimeDate) {
        if (showtimeDate == null) {
            return Set.of();
        }

        try {
            ListActiveFilmIdsByShowtimeDateReply reply = showtimeBlockingStub.listActiveFilmIdsByShowtimeDate(
                    ListActiveFilmIdsByShowtimeDateRequest.newBuilder()
                            .setShowtimeDate(showtimeDate.toString())
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.SHOWTIME_SERVICE_ERROR));
            }

            Set<UUID> filmIds = new LinkedHashSet<>();
            reply.getFilmIdsList().stream()
                    .filter(filmId -> filmId != null && !filmId.isBlank())
                    .forEach(filmId -> filmIds.add(UUID.fromString(filmId)));
            return filmIds;
        } catch (BusinessException ex) {
            throw ex;
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.SHOWTIME_SERVICE_ERROR);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.SHOWTIME_SERVICE_ERROR);
        }
    }
}
