package com.cinema.hall_services.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.hall.GetHallByIdReply;
import com.cinema.grpc.hall.GetHallByIdRequest;
import com.cinema.grpc.hall.HallInternalServiceGrpc;
import com.cinema.grpc.hall.HallPayload;
import com.cinema.hall_services.dto.response.HallResponse;
import com.cinema.hall_services.services.HallService;
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
public class HallInternalGrpcService extends HallInternalServiceGrpc.HallInternalServiceImplBase
        implements BindableService {

    private final HallService hallService;

    @Override
    public void getHallById(GetHallByIdRequest request, StreamObserver<GetHallByIdReply> responseObserver) {
        try {
            HallResponse hall = hallService.getHallById(UUID.fromString(request.getHallId()));
            responseObserver.onNext(GetHallByIdReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("Hall fetched successfully")
                    .setHall(toPayload(hall))
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(GetHallByIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ex.getErrorCode().name())
                    .setMessage(ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while fetching hall", ex);
            responseObserver.onNext(GetHallByIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    private HallPayload toPayload(HallResponse hall) {
        return HallPayload.newBuilder()
                .setId(hall.getId().toString())
                .setCinemaId(hall.getCinemaId().toString())
                .setName(Objects.toString(hall.getName(), ""))
                .setLayoutJson(hall.getLayoutJson() == null ? "" : hall.getLayoutJson().toString())
                .build();
    }
}
