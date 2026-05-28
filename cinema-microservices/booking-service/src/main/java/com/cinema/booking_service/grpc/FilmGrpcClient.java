package com.cinema.booking_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.film.FilmInternalServiceGrpc;
import com.cinema.grpc.film.FilmPayload;
import com.cinema.grpc.film.GetFilmByIdReply;
import com.cinema.grpc.film.GetFilmByIdRequest;
import io.grpc.StatusRuntimeException;
import lombok.Builder;
import lombok.Getter;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class FilmGrpcClient {

    private final FilmInternalServiceGrpc.FilmInternalServiceBlockingStub filmBlockingStub;

    public FilmGrpcClient(GrpcChannelFactory channelFactory) {
        this.filmBlockingStub = FilmInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("film"));
    }

    public FilmSnapshot getFilmById(UUID filmId) {
        try {
            GetFilmByIdReply reply = filmBlockingStub.getFilmById(GetFilmByIdRequest.newBuilder()
                    .setFilmId(filmId.toString())
                    .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.FILM_SERVICE_ERROR));
            }

            return toSnapshot(reply.getFilm());
        } catch (BusinessException ex) {
            throw ex;
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.FILM_SERVICE_ERROR);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.FILM_SERVICE_ERROR);
        }
    }

    private FilmSnapshot toSnapshot(FilmPayload payload) {
        return FilmSnapshot.builder()
                .id(UUID.fromString(payload.getId()))
                .title(payload.getTitle())
                .build();
    }

    @Getter
    @Builder
    public static class FilmSnapshot {
        private UUID id;
        private String title;
    }
}
