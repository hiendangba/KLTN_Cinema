package com.cinema.booking_service.grpc;

import com.cinema.booking_service.enums.BookingStatus;
import com.cinema.booking_service.repository.BookingRepository;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.booking.BookingInternalServiceGrpc;
import com.cinema.grpc.booking.CheckShowtimeBookedReply;
import com.cinema.grpc.booking.CheckShowtimeBookedRequest;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingInternalGrpcService extends BookingInternalServiceGrpc.BookingInternalServiceImplBase
        implements BindableService {

    private final BookingRepository bookingRepository;

    @Override
    public void checkShowtimeBooked(
            CheckShowtimeBookedRequest request,
            StreamObserver<CheckShowtimeBookedReply> responseObserver) {
        UUID showtimeId;
        try {
            showtimeId = UUID.fromString(request.getShowtimeId());
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(CheckShowtimeBookedReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INVALID_FORMAT.name())
                    .setMessage(ErrorCode.INVALID_FORMAT.getMessage())
                    .build());
            responseObserver.onCompleted();
            return;
        }

        try {
            boolean booked = bookingRepository.existsByShowtimeIdAndIsDeletedFalseAndBookingStatusIn(
                    showtimeId,
                    EnumSet.of(BookingStatus.PENDING, BookingStatus.RESERVED, BookingStatus.CONFIRMED));

            responseObserver.onNext(CheckShowtimeBookedReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("Showtime booking status fetched successfully")
                    .setBooked(booked)
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(CheckShowtimeBookedReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ex.getErrorCode().name())
                    .setMessage(ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while checking showtime booked status", ex);
            responseObserver.onNext(CheckShowtimeBookedReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }
}

