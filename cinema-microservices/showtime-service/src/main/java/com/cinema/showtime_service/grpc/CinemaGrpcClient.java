package com.cinema.showtime_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.cinema.CinemaInternalServiceGrpc;
import com.cinema.grpc.cinema.GetCinemaByIdReply;
import com.cinema.grpc.cinema.GetCinemaByIdRequest;
import com.cinema.grpc.cinema.GetCinemaByUserIdReply;
import com.cinema.grpc.cinema.GetCinemaByUserIdRequest;
import com.cinema.grpc.cinema.GetCinemasByUserIdReply;
import com.cinema.showtime_service.dto.response.CinemaOperatingHoursResponse;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
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

    public CinemaOperatingHoursResponse getCinemaById(UUID cinemaId) {
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

            return CinemaOperatingHoursResponse.builder()
                    .id(UUID.fromString(reply.getCinema().getId()))
                    .name(reply.getCinema().getName().isBlank() ? null : reply.getCinema().getName())
                    .openTime(parseLocalTime(reply.getCinema().getOpenTime()))
                    .closeTime(parseLocalTime(reply.getCinema().getCloseTime()))
                    .build();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR);
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR);
        }
    }

    private LocalTime parseLocalTime(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR);
        }
        try {
            return LocalTime.parse(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR);
        }
    }
}
