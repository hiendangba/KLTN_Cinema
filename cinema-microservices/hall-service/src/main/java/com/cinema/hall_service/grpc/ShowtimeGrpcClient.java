package com.cinema.hall_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.showtime.ListActiveShowtimeIdsByHallReply;
import com.cinema.grpc.showtime.ListActiveShowtimeIdsByHallRequest;
import com.cinema.grpc.showtime.ShowtimeInternalServiceGrpc;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ShowtimeGrpcClient {

    private final ShowtimeInternalServiceGrpc.ShowtimeInternalServiceBlockingStub showtimeBlockingStub;

    public ShowtimeGrpcClient(GrpcChannelFactory channelFactory) {
        this.showtimeBlockingStub = ShowtimeInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("showtime"));
    }

    public List<UUID> listActiveShowtimeIdsByHall(UUID hallId) {
        try {
            ListActiveShowtimeIdsByHallReply reply = showtimeBlockingStub.listActiveShowtimeIdsByHall(
                    ListActiveShowtimeIdsByHallRequest.newBuilder()
                            .setHallId(hallId.toString())
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.SHOWTIME_SERVICE_ERROR));
            }

            return reply.getShowtimeIdsList().stream()
                    .map(UUID::fromString)
                    .toList();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.SHOWTIME_SERVICE_ERROR);
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.SHOWTIME_SERVICE_ERROR);
        }
    }
}
