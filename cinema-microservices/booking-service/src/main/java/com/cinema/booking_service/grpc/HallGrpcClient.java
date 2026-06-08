package com.cinema.booking_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.hall.GetHallByIdReply;
import com.cinema.grpc.hall.GetHallByIdRequest;
import com.cinema.grpc.hall.HallInternalServiceGrpc;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class HallGrpcClient {

    private final HallInternalServiceGrpc.HallInternalServiceBlockingStub hallBlockingStub;

    public HallGrpcClient(GrpcChannelFactory channelFactory) {
        this.hallBlockingStub = HallInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("hall"));
    }

    public HallSummary getHallById(UUID hallId) {
        try {
            GetHallByIdReply reply = hallBlockingStub.getHallById(
                    GetHallByIdRequest.newBuilder()
                            .setHallId(hallId.toString())
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.HALL_SERVICE_ERROR));
            }

            if (reply.getHall() == null || reply.getHall().getId().isBlank()
                    || reply.getHall().getCinemaId().isBlank()) {
                throw new BusinessException(ErrorCode.HALL_NOT_FOUND);
            }

            try {
                return new HallSummary(
                        UUID.fromString(reply.getHall().getId()),
                        UUID.fromString(reply.getHall().getCinemaId()),
                        reply.getHall().getName());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.HALL_SERVICE_ERROR);
            }
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.HALL_SERVICE_ERROR);
        }
    }

    public record HallSummary(UUID id, UUID cinemaId, String name) {
    }
}
