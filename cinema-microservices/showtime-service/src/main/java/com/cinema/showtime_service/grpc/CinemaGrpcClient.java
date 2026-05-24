package com.cinema.showtime_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.cinema.CinemaInternalServiceGrpc;
import com.cinema.grpc.cinema.GetCinemaByUserIdReply;
import com.cinema.grpc.cinema.GetCinemaByUserIdRequest;
import com.cinema.grpc.cinema.GetCinemasByUserIdReply;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class CinemaGrpcClient {

    private final CinemaInternalServiceGrpc.CinemaInternalServiceBlockingStub cinemaBlockingStub;

    public CinemaGrpcClient(GrpcChannelFactory channelFactory) {
        this.cinemaBlockingStub = CinemaInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("cinema"));
    }

    public UUID getCinemaIdByUserId(UUID userId) {
        try {
            GetCinemaByUserIdReply reply = cinemaBlockingStub.getCinemaByUserId(
                    GetCinemaByUserIdRequest.newBuilder()
                            .setUserId(userId.toString())
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.CINEMA_SERVICE_ERROR));
            }

            if (reply.getCinema() == null || reply.getCinema().getId().isBlank()) {
                throw new BusinessException(ErrorCode.CINEMA_NOT_FOUND);
            }

            try {
                return UUID.fromString(reply.getCinema().getId());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR);
            }
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR);
        }
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
                    .filter(cinema -> cinema != null && !cinema.getId().isBlank())
                    .map(cinema -> {
                        try {
                            return UUID.fromString(cinema.getId());
                        } catch (IllegalArgumentException ex) {
                            throw new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR);
                        }
                    })
                    .collect(Collectors.toList());
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR);
        }
    }
}
