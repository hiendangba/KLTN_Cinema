package com.cinema.showtime_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.film.FilmInternalServiceGrpc;
import com.cinema.grpc.film.GetFilmByIdReply;
import com.cinema.grpc.film.GetFilmByIdRequest;
import com.cinema.grpc.film.GetFilmsByIdsReply;
import com.cinema.grpc.film.GetFilmsByIdsRequest;
import com.cinema.showtime_service.dto.response.FilmResponse;
import com.cinema.showtime_service.mapper.FilmMapper;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class FilmGrpcClient {

    private final FilmInternalServiceGrpc.FilmInternalServiceBlockingStub filmBlockingStub;
    private final FilmMapper filmMapper;

    public FilmGrpcClient(GrpcChannelFactory channelFactory, FilmMapper filmMapper) {
        this.filmBlockingStub = FilmInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("film"));
        this.filmMapper = filmMapper;
    }

    public FilmResponse getFilmById(UUID filmId) {
        try {
            GetFilmByIdReply reply = filmBlockingStub.getFilmById(GetFilmByIdRequest.newBuilder()
                    .setFilmId(filmId.toString())
                    .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.FILM_SERVICE_ERROR));
            }

            return filmMapper.toResponse(reply.getFilm());
        } catch (BusinessException ex) {
            throw ex;
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.FILM_SERVICE_ERROR);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.FILM_SERVICE_ERROR);
        }
    }

    public Map<UUID, FilmResponse> getFilmsByIds(List<UUID> filmIds) {
        try {
            GetFilmsByIdsReply reply = filmBlockingStub.getFilmsByIds(GetFilmsByIdsRequest.newBuilder()
                    .addAllFilmIds(filmIds.stream().map(UUID::toString).collect(Collectors.toList()))
                    .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.FILM_SERVICE_ERROR));
            }

            return reply.getFilmsList().stream()
                    .map(filmMapper::toResponse)
                    .filter(response -> response.getId() != null)
                    .collect(Collectors.toMap(FilmResponse::getId, response -> response, (a, b) -> a));
        } catch (BusinessException ex) {
            throw ex;
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.FILM_SERVICE_ERROR);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.FILM_SERVICE_ERROR);
        }
    }

}
