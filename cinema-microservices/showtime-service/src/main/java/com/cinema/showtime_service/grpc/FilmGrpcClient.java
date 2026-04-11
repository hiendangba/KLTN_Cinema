package com.cinema.showtime_service.grpc;

import com.cinema.Enum.FilmEnum;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.film.FilmInternalServiceGrpc;
import com.cinema.grpc.film.FilmPayload;
import com.cinema.grpc.film.GetFilmByIdReply;
import com.cinema.grpc.film.GetFilmByIdRequest;
import com.cinema.grpc.film.GetFilmsByIdsReply;
import com.cinema.grpc.film.GetFilmsByIdsRequest;
import com.cinema.showtime_service.dto.response.FilmResponse;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class FilmGrpcClient {

    private final FilmInternalServiceGrpc.FilmInternalServiceBlockingStub filmBlockingStub;

    public FilmGrpcClient(GrpcChannelFactory channelFactory) {
        this.filmBlockingStub = FilmInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("film"));
    }

    public FilmResponse getFilmById(UUID filmId) {
        try {
            GetFilmByIdReply reply = filmBlockingStub.getFilmById(GetFilmByIdRequest.newBuilder()
                    .setFilmId(filmId.toString())
                    .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.FILM_SERVICE_ERROR));
            }

            return toResponse(reply.getFilm());
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
                    .map(this::toResponse)
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

    private FilmResponse toResponse(FilmPayload payload) {
        return FilmResponse.builder()
                .id(UUID.fromString(payload.getId()))
                .title(payload.getTitle())
                .director(emptyToNull(payload.getDirector()))
                .actor(emptyToNull(payload.getActor()))
                .type(emptyToNull(payload.getType()))
                .releaseDate(payload.getReleaseDate().isBlank() ? null : LocalDate.parse(payload.getReleaseDate()))
                .description(emptyToNull(payload.getDescription()))
                .duration(payload.getDuration())
                .poster(emptyToNull(payload.getPoster()))
                .trailer(emptyToNull(payload.getTrailer()))
                .country(emptyToNull(payload.getCountry()))
                .language(emptyToNull(payload.getLanguage()))
                .ageRating(payload.getAgeRating().isBlank() ? null : FilmEnum.AgeRating.valueOf(payload.getAgeRating()))
                .status(payload.getStatus().isBlank() ? null : FilmEnum.FilmStatus.valueOf(payload.getStatus()))
                .timeCreated(payload.getTimeCreated().isBlank() ? null : LocalDateTime.parse(payload.getTimeCreated()))
                .timeUpdated(payload.getTimeUpdated().isBlank() ? null : LocalDateTime.parse(payload.getTimeUpdated()))
                .build();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
