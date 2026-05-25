package com.cinema.cinema_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.hall.HallInternalServiceGrpc;
import com.cinema.grpc.hall.ListActiveHallIdsByCinemaReply;
import com.cinema.grpc.hall.ListActiveHallIdsByCinemaRequest;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class HallGrpcClient {

    private final HallInternalServiceGrpc.HallInternalServiceBlockingStub hallBlockingStub;

    public HallGrpcClient(GrpcChannelFactory channelFactory) {
        this.hallBlockingStub = HallInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("hall"));
    }

    public List<UUID> listActiveHallIdsByCinema(UUID cinemaId) {
        try {
            ListActiveHallIdsByCinemaReply reply = hallBlockingStub.listActiveHallIdsByCinema(
                    ListActiveHallIdsByCinemaRequest.newBuilder()
                            .setCinemaId(cinemaId.toString())
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.HALL_SERVICE_ERROR));
            }
            return reply.getHallIdsList().stream().map(UUID::fromString).toList();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.HALL_SERVICE_ERROR);
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.HALL_SERVICE_ERROR);
        }
    }
}
