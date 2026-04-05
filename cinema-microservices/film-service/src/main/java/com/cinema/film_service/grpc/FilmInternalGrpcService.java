package com.cinema.film_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.film_service.dto.response.FilmResponse;
import com.cinema.film_service.services.FilmService;
import com.cinema.grpc.film.FilmInternalServiceGrpc;
import com.cinema.grpc.film.FilmPayload;
import com.cinema.grpc.film.GetFilmByIdReply;
import com.cinema.grpc.film.GetFilmByIdRequest;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class FilmInternalGrpcService extends FilmInternalServiceGrpc.FilmInternalServiceImplBase implements BindableService {

    private final FilmService filmService;

    @Override
    public void getFilmById(
            GetFilmByIdRequest request,
            StreamObserver<GetFilmByIdReply> responseObserver) {
        try {
            FilmResponse film = filmService.getFilmById(UUID.fromString(request.getFilmId()));
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
