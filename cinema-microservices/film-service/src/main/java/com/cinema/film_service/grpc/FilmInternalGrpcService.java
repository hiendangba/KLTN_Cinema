package com.cinema.film_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.film_service.dto.response.FilmResponse;
import com.cinema.film_service.dto.response.BatchFilmResponse;
import com.cinema.film_service.services.FilmService;
import com.cinema.grpc.film.FilmInternalServiceGrpc;
import com.cinema.grpc.film.FilmPayload;
import com.cinema.grpc.film.GetFilmByIdReply;
import com.cinema.grpc.film.GetFilmByIdRequest;
import com.cinema.grpc.film.GetFilmsByIdsReply;
import com.cinema.grpc.film.GetFilmsByIdsRequest;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class FilmInternalGrpcService extends FilmInternalServiceGrpc.FilmInternalServiceImplBase implements BindableService {

    private final FilmService filmService;

    @Override
    public void getFilmById(
            GetFilmByIdRequest request,
            StreamObserver<GetFilmByIdReply> responseObserver) {
        UUID filmId;
        try {
            filmId = UUID.fromString(request.getFilmId());
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(GetFilmByIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INVALID_FORMAT.name())
                    .setMessage(ErrorCode.INVALID_FORMAT.getMessage())
                    .build());
            responseObserver.onCompleted();
            return;
        }

        try {
            FilmResponse film = filmService.getFilmById(filmId);
            responseObserver.onNext(GetFilmByIdReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("Film fetched successfully")
                    .setFilm(toPayload(film))
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(GetFilmByIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ex.getErrorCode().name())
                    .setMessage(ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while fetching film", ex);
            responseObserver.onNext(GetFilmByIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getFilmsByIds(
            GetFilmsByIdsRequest request,
            StreamObserver<GetFilmsByIdsReply> responseObserver) {
        List<UUID> ids;
        try {
            ids = request.getFilmIdsList().stream()
                    .map(UUID::fromString)
                    .collect(Collectors.toList());
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(GetFilmsByIdsReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INVALID_FORMAT.name())
                    .setMessage(ErrorCode.INVALID_FORMAT.getMessage())
                    .build());
            responseObserver.onCompleted();
            return;
        }

        try {
            com.cinema.film_service.dto.request.BatchFilmRequest batchRequest =
                    new com.cinema.film_service.dto.request.BatchFilmRequest();
            batchRequest.setIds(ids);

            BatchFilmResponse batchResponse = filmService.getFilmsInBatch(batchRequest);

            List<FilmPayload> payloads = batchResponse.getData().stream()
                    .map(this::toPayload)
                    .collect(Collectors.toList());

            responseObserver.onNext(GetFilmsByIdsReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("Films fetched successfully")
                    .addAllFilms(payloads)
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(GetFilmsByIdsReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ex.getErrorCode().name())
                    .setMessage(ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while fetching films", ex);
            responseObserver.onNext(GetFilmsByIdsReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    private FilmPayload toPayload(FilmResponse film) {
        FilmPayload.Builder builder = FilmPayload.newBuilder()
                .setId(film.getId().toString())
                .setTitle(Objects.toString(film.getTitle(), ""))
                .setDirector(Objects.toString(film.getDirector(), ""))
                .setActor(Objects.toString(film.getActor(), ""))
                .setType(Objects.toString(film.getType(), ""))
                .setReleaseDate(Objects.toString(film.getReleaseDate(), ""))
                .setDescription(Objects.toString(film.getDescription(), ""))
                .setDuration(film.getDuration() == null ? 0 : film.getDuration())
                .setPoster(Objects.toString(film.getPoster(), ""))
                .setTrailer(Objects.toString(film.getTrailer(), ""))
                .setCountry(Objects.toString(film.getCountry(), ""))
                .setLanguage(Objects.toString(film.getLanguage(), ""))
                .setAgeRating(film.getAgeRating() == null ? "" : film.getAgeRating().name())
                .setStatus(film.getStatus() == null ? "" : film.getStatus().name())
                .setTimeCreated(Objects.toString(film.getTimeCreated(), ""))
                .setTimeUpdated(Objects.toString(film.getTimeUpdated(), ""));
        return builder.build();
    }
}
