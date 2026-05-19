package com.cinema.showtime_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.showtime.GetShowtimeByIdReply;
import com.cinema.grpc.showtime.GetShowtimeByIdRequest;
import com.cinema.grpc.showtime.ListActiveShowtimeIdsByHallReply;
import com.cinema.grpc.showtime.ListActiveShowtimeIdsByHallRequest;
import com.cinema.grpc.showtime.ShowtimeInternalServiceGrpc;
import com.cinema.grpc.showtime.ShowtimePayload;
import com.cinema.showtime_service.entity.ShowTime;
import com.cinema.showtime_service.repository.ShowTimeRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShowtimeInternalGrpcService extends ShowtimeInternalServiceGrpc.ShowtimeInternalServiceImplBase {

    private final ShowTimeRepository showTimeRepository;
    private final HallGrpcClient hallGrpcClient;

    @Override
    public void getShowtimeById(GetShowtimeByIdRequest request, StreamObserver<GetShowtimeByIdReply> responseObserver) {
        UUID showtimeId;
        try {
            showtimeId = UUID.fromString(request.getShowtimeId());
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(GetShowtimeByIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INVALID_FORMAT.name())
                    .setMessage(ErrorCode.INVALID_FORMAT.getMessage())
                    .build());
            responseObserver.onCompleted();
            return;
        }

        try {
            ShowTime showTime = showTimeRepository.findById(showtimeId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SHOWTIME_NOT_FOUND));
            UUID cinemaId = hallGrpcClient.getCinemaIdByHallId(showTime.getHallId());

            responseObserver.onNext(GetShowtimeByIdReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("Showtime fetched successfully")
                    .setShowtime(ShowtimePayload.newBuilder()
                            .setId(showTime.getId().toString())
                            .setHallId(showTime.getHallId().toString())
                            .setCinemaId(cinemaId.toString())
                            .setPricingPolicyId(showTime.getPricingPolicyId().toString())
                            .build())
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(GetShowtimeByIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ex.getErrorCode().name())
                    .setMessage(ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while fetching showtime", ex);
            responseObserver.onNext(GetShowtimeByIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void listActiveShowtimeIdsByHall(ListActiveShowtimeIdsByHallRequest request,
                                            StreamObserver<ListActiveShowtimeIdsByHallReply> responseObserver) {
        UUID hallId;
        try {
            hallId = UUID.fromString(request.getHallId());
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(ListActiveShowtimeIdsByHallReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INVALID_FORMAT.name())
                    .setMessage(ErrorCode.INVALID_FORMAT.getMessage())
                    .build());
            responseObserver.onCompleted();
            return;
        }

        try {
            ListActiveShowtimeIdsByHallReply.Builder builder = ListActiveShowtimeIdsByHallReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("Active showtime ids fetched successfully");

            showTimeRepository.findActiveShowtimeIdsByHallId(hallId)
                    .forEach(id -> builder.addShowtimeIds(id.toString()));

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(ListActiveShowtimeIdsByHallReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ex.getErrorCode().name())
                    .setMessage(ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while fetching active showtime ids by hall", ex);
            responseObserver.onNext(ListActiveShowtimeIdsByHallReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }
}
