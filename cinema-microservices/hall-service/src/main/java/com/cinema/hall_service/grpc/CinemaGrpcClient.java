package com.cinema.hall_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.cinema.CinemaInternalServiceGrpc;
import com.cinema.grpc.cinema.GetCinemaByIdReply;
import com.cinema.grpc.cinema.GetCinemaByIdRequest;
import com.cinema.grpc.cinema.GetCinemaByUserIdReply;
import com.cinema.grpc.cinema.GetCinemaByUserIdRequest;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

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
