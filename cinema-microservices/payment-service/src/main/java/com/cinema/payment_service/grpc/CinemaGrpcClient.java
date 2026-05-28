package com.cinema.payment_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.cinema.CinemaInternalServiceGrpc;
import com.cinema.grpc.cinema.CinemaPayload;
import com.cinema.grpc.cinema.GetAllActiveCinemasReply;
import com.cinema.grpc.cinema.GetAllActiveCinemasRequest;
import com.cinema.grpc.cinema.GetCinemaByIdReply;
import com.cinema.grpc.cinema.GetCinemaByIdRequest;
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

    public List<CinemaSummary> getCinemasByUserId(UUID userId, String role) {
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
                    .map(this::toSummary)
                    .toList();
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR);
        }
    }

    public List<CinemaSummary> getAllActiveCinemas() {
        try {
            GetAllActiveCinemasReply reply = cinemaBlockingStub.getAllActiveCinemas(
                    GetAllActiveCinemasRequest.newBuilder().build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.CINEMA_SERVICE_ERROR));
            }

            return reply.getCinemasList().stream()
                    .map(this::toSummary)
                    .toList();
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR);
        }
    }

    public CinemaSummary getCinemaById(UUID cinemaId) {
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
            return toSummary(reply.getCinema());
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR);
        }
    }

    private CinemaSummary toSummary(CinemaPayload payload) {
        if (payload == null || payload.getId().isBlank()) {
            throw new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR);
        }
        try {
            return new CinemaSummary(
                    UUID.fromString(payload.getId()),
                    payload.getName());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR);
        }
    }

    public record CinemaSummary(UUID id, String name) {
    }
}
