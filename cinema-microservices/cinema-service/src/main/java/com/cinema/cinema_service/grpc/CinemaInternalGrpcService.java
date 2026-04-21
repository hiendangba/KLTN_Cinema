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
            responseObserver.onNext(GetCinemaByUserIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INVALID_FORMAT.name())
                    .setMessage(ErrorCode.INVALID_FORMAT.getMessage())
                    .build());
            responseObserver.onCompleted();
            return;
        }

        try {
            CinemaResponse cinema = cinemaService.getCinemaByManagerId(userId);
            responseObserver.onNext(GetCinemaByUserIdReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("Cinema fetched successfully")
                    .setCinema(toPayload(cinema))
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(GetCinemaByUserIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ex.getErrorCode().name())
                    .setMessage(ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while fetching cinema by user id", ex);
            responseObserver.onNext(GetCinemaByUserIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getCinemaById(GetCinemaByIdRequest request, StreamObserver<GetCinemaByIdReply> responseObserver) {
        UUID cinemaId;
        try {
            cinemaId = UUID.fromString(request.getCinemaId());
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(GetCinemaByIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INVALID_FORMAT.name())
                    .setMessage(ErrorCode.INVALID_FORMAT.getMessage())
                    .build());
            responseObserver.onCompleted();
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
            responseObserver.onNext(GetCinemaByIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ex.getErrorCode().name())
                    .setMessage(ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while fetching cinema by id", ex);
            responseObserver.onNext(GetCinemaByIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    private CinemaPayload toPayload(CinemaResponse cinema) {
        return CinemaPayload.newBuilder()
                .setId(cinema.getId().toString())
                .setName(Objects.toString(cinema.getName(), ""))
                .build();
    }
}
