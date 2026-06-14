package com.cinema.film_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.cinema.CinemaInternalServiceGrpc;
import com.cinema.grpc.cinema.GetCinemaByUserIdRequest;
import com.cinema.grpc.cinema.GetCinemasByUserIdReply;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class CinemaGrpcClient {

    private final CinemaInternalServiceGrpc.CinemaInternalServiceBlockingStub cinemaBlockingStub;

    public CinemaGrpcClient(GrpcChannelFactory channelFactory) {
        this.cinemaBlockingStub = CinemaInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("cinema"));
    }

    public List<UUID> getCinemaIdsByUserId(UUID userId, String role) {
        try {
            GetCinemasByUserIdReply reply = cinemaBlockingStub.getCinemasByUserId(
                    GetCinemaByUserIdRequest.newBuilder()
                            .setUserId(userId.toString())
                            .setRole(role == null ? "" : role)
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.CINEMA_SERVICE_ERROR));
            }

            return reply.getCinemasList().stream()
                    .filter(cinema -> cinema != null && cinema.getId() != null && !cinema.getId().isBlank())
                    .map(cinema -> {
                        try {
                            return UUID.fromString(cinema.getId());
                        } catch (IllegalArgumentException ex) {
                            throw new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR);
                        }
                    })
                    .toList();
        } catch (BusinessException ex) {
            throw ex;
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR);
        }
    }
}
