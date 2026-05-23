package com.cinema.hall_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.cinema.CinemaInternalServiceGrpc;
import com.cinema.grpc.cinema.GetCinemaByIdReply;
import com.cinema.grpc.cinema.GetCinemaByIdRequest;
import com.cinema.grpc.cinema.GetCinemaByUserIdReply;
import com.cinema.grpc.cinema.GetCinemaByUserIdRequest;
import com.cinema.grpc.cinema.GetCinemasByUserIdReply;
import com.cinema.http.HeaderNames;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class CinemaGrpcClient {

    private final CinemaInternalServiceGrpc.CinemaInternalServiceBlockingStub cinemaBlockingStub;

    public CinemaGrpcClient(GrpcChannelFactory channelFactory) {
        this.cinemaBlockingStub = CinemaInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("cinema"));
    }

    public UUID getCinemaIdByUserId(UUID userId) {
        List<UUID> cinemaIds = getCinemaIdsByUserId(userId, HeaderNames.ROLE_MANAGER);
        if (cinemaIds.isEmpty()) {
            throw new BusinessException(ErrorCode.CINEMA_NOT_FOUND);
        }
        return cinemaIds.get(0);
    }

    public List<UUID> getCinemaIdsByUserId(UUID userId) {
        return getCinemaIdsByUserId(userId, HeaderNames.ROLE_MANAGER);
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

            List<UUID> cinemaIds = new ArrayList<>();
            for (var cinema : reply.getCinemasList()) {
                if (cinema == null || cinema.getId().isBlank()) {
                    continue;
                }
                try {
                    cinemaIds.add(UUID.fromString(cinema.getId()));
                } catch (IllegalArgumentException ex) {
                    throw new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR);
                }
            }
            return cinemaIds;
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR);
        }
    }

    public String getCinemaNameById(UUID cinemaId) {
        try {
            GetCinemaByIdReply reply = cinemaBlockingStub.getCinemaById(
                    GetCinemaByIdRequest.newBuilder()
                            .setCinemaId(cinemaId.toString())
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.CINEMA_SERVICE_ERROR));
            }

            if (reply.getCinema() == null || reply.getCinema().getId().isBlank()) {
                throw new BusinessException(ErrorCode.CINEMA_NOT_FOUND);
            }

            return reply.getCinema().getName().isBlank() ? null : reply.getCinema().getName();
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR);
        }
    }
}
