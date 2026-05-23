package com.cinema.cinema_service.grpc;

import com.cinema.cinema_service.dto.response.CinemaResponse;
import com.cinema.cinema_service.services.CinemaService;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.cinema.CinemaInternalServiceGrpc;
import com.cinema.grpc.cinema.CinemaPayload;
import com.cinema.grpc.cinema.GetCinemaByIdReply;
import com.cinema.grpc.cinema.GetCinemaByIdRequest;
import com.cinema.grpc.cinema.GetCinemaByUserIdReply;
import com.cinema.grpc.cinema.GetCinemaByUserIdRequest;
import com.cinema.grpc.cinema.GetCinemasByUserIdReply;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CinemaInternalGrpcService extends CinemaInternalServiceGrpc.CinemaInternalServiceImplBase
        implements BindableService {

    private final CinemaService cinemaService;

    @Override
    public void getCinemaByUserId(GetCinemaByUserIdRequest request,
                                  StreamObserver<GetCinemaByUserIdReply> responseObserver) {
        UUID userId;
        try {
            userId = UUID.fromString(request.getUserId());
        } catch (IllegalArgumentException ex) {
            failCinemaByUserId(responseObserver, ErrorCode.INVALID_FORMAT.name(), ErrorCode.INVALID_FORMAT.getMessage());
            return;
        }

        try {
            List<CinemaResponse> cinemas = cinemaService.getAccessibleCinemasByUserId(userId, request.getRole());
            CinemaResponse cinema = cinemas.isEmpty() ? null : cinemas.get(0);
            responseObserver.onNext(GetCinemaByUserIdReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("Cinema fetched successfully")
                    .setCinema(cinema == null ? CinemaPayload.newBuilder().build() : toPayload(cinema))
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            failCinemaByUserId(responseObserver, ex.getErrorCode().name(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while fetching cinema by user id", ex);
            failCinemaByUserId(responseObserver, ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.getMessage());
        }
    }

    @Override
    public void getCinemasByUserId(GetCinemaByUserIdRequest request,
                                   StreamObserver<GetCinemasByUserIdReply> responseObserver) {
        UUID userId;
        try {
            userId = UUID.fromString(request.getUserId());
        } catch (IllegalArgumentException ex) {
            failCinemasByUserId(responseObserver, ErrorCode.INVALID_FORMAT.name(), ErrorCode.INVALID_FORMAT.getMessage());
            return;
        }

        try {
            List<CinemaResponse> cinemas = cinemaService.getAccessibleCinemasByUserId(userId, request.getRole());
            responseObserver.onNext(GetCinemasByUserIdReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("Cinemas fetched successfully")
                    .addAllCinemas(cinemas.stream().map(this::toPayload).toList())
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            failCinemasByUserId(responseObserver, ex.getErrorCode().name(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while fetching cinemas by user id", ex);
            failCinemasByUserId(responseObserver, ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.getMessage());
        }
    }

    @Override
    public void getCinemaById(GetCinemaByIdRequest request, StreamObserver<GetCinemaByIdReply> responseObserver) {
        UUID cinemaId;
        try {
            cinemaId = UUID.fromString(request.getCinemaId());
        } catch (IllegalArgumentException ex) {
            failCinemaById(responseObserver, ErrorCode.INVALID_FORMAT.name(), ErrorCode.INVALID_FORMAT.getMessage());
            return;
        }

        try {
            CinemaResponse cinema = cinemaService.getCinemaById(cinemaId);
            responseObserver.onNext(GetCinemaByIdReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("Cinema fetched successfully")
                    .setCinema(toPayload(cinema))
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            failCinemaById(responseObserver, ex.getErrorCode().name(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while fetching cinema by id", ex);
            failCinemaById(responseObserver, ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.getMessage());
        }
    }

    private void failCinemaByUserId(StreamObserver<GetCinemaByUserIdReply> responseObserver, String errorKey, String message) {
        responseObserver.onNext(GetCinemaByUserIdReply.newBuilder()
                .setSuccess(false)
                .setErrorKey(errorKey)
                .setMessage(message)
                .build());
        responseObserver.onCompleted();
    }

    private void failCinemasByUserId(StreamObserver<GetCinemasByUserIdReply> responseObserver, String errorKey, String message) {
        responseObserver.onNext(GetCinemasByUserIdReply.newBuilder()
                .setSuccess(false)
                .setErrorKey(errorKey)
                .setMessage(message)
                .build());
        responseObserver.onCompleted();
    }

    private void failCinemaById(StreamObserver<GetCinemaByIdReply> responseObserver, String errorKey, String message) {
        responseObserver.onNext(GetCinemaByIdReply.newBuilder()
                .setSuccess(false)
                .setErrorKey(errorKey)
                .setMessage(message)
                .build());
        responseObserver.onCompleted();
    }

    private CinemaPayload toPayload(CinemaResponse cinema) {
        return CinemaPayload.newBuilder()
                .setId(cinema.getId().toString())
                .setName(Objects.toString(cinema.getName(), ""))
                .build();
    }
}
