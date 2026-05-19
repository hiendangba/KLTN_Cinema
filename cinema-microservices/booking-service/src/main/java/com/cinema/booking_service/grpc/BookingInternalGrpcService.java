package com.cinema.booking_service.grpc;

import com.cinema.booking_service.enums.BookingStatus;
import com.cinema.booking_service.repository.BookingRepository;
import com.cinema.booking_service.repository.BookingSeatItemRepository;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.booking.BookingInternalServiceGrpc;
import com.cinema.grpc.booking.CheckShowtimeBookedReply;
import com.cinema.grpc.booking.CheckShowtimeBookedRequest;
import com.cinema.grpc.booking.GetSeatRuntimeStatesReply;
import com.cinema.grpc.booking.GetSeatRuntimeStatesRequest;
import com.cinema.grpc.booking.HasActiveBookingByShowtimeIdsReply;
import com.cinema.grpc.booking.HasActiveBookingByShowtimeIdsRequest;
import com.cinema.grpc.booking.SeatRuntimeStatePayload;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingInternalGrpcService extends BookingInternalServiceGrpc.BookingInternalServiceImplBase
        implements BindableService {

    private final BookingRepository bookingRepository;
    private final BookingSeatItemRepository bookingSeatItemRepository;

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

    @Override
    public void getSeatRuntimeStates(GetSeatRuntimeStatesRequest request,
                                     StreamObserver<GetSeatRuntimeStatesReply> responseObserver) {
        UUID showtimeId;
        try {
            showtimeId = UUID.fromString(request.getShowtimeId());
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(GetSeatRuntimeStatesReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INVALID_FORMAT.name())
                    .setMessage(ErrorCode.INVALID_FORMAT.getMessage())
                    .build());
            responseObserver.onCompleted();
            return;
        }

        try {
            List<String> normalizedSeatCodes = request.getSeatCodesList().stream()
                    .map(code -> code == null ? "" : code.trim().toUpperCase(Locale.ROOT))
                    .filter(code -> !code.isBlank())
                    .toList();

            Set<String> locked = bookingSeatItemRepository.findSeatCodesByShowtimeAndStatuses(
                            showtimeId,
                            normalizedSeatCodes,
                            EnumSet.of(BookingStatus.RESERVED, BookingStatus.PENDING))
                    .stream()
                    .collect(Collectors.toSet());
            Set<String> booked = bookingSeatItemRepository.findSeatCodesByShowtimeAndStatuses(
                            showtimeId,
                            normalizedSeatCodes,
                            EnumSet.of(BookingStatus.CONFIRMED))
                    .stream()
                    .collect(Collectors.toSet());

            GetSeatRuntimeStatesReply.Builder builder = GetSeatRuntimeStatesReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("Seat runtime states fetched successfully");

            for (String seatCode : normalizedSeatCodes) {
                String state = "AVAILABLE";
                if (locked.contains(seatCode)) {
                    state = "LOCKED";
                }
                if (booked.contains(seatCode)) {
                    state = "BOOKED";
                }
                builder.addSeatStates(SeatRuntimeStatePayload.newBuilder()
                        .setSeatCode(seatCode)
                        .setState(state)
                        .build());
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(GetSeatRuntimeStatesReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ex.getErrorCode().name())
                    .setMessage(ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while fetching seat runtime states", ex);
            responseObserver.onNext(GetSeatRuntimeStatesReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void hasActiveBookingByShowtimeIds(HasActiveBookingByShowtimeIdsRequest request,
                                              StreamObserver<HasActiveBookingByShowtimeIdsReply> responseObserver) {
        try {
            Set<UUID> showtimeIds = request.getShowtimeIdsList().stream()
                    .map(raw -> {
                        try {
                            return UUID.fromString(raw);
                        } catch (IllegalArgumentException ex) {
                            throw new BusinessException(ErrorCode.INVALID_FORMAT);
                        }
                    })
                    .collect(Collectors.toSet());

            if (showtimeIds.isEmpty()) {
                responseObserver.onNext(HasActiveBookingByShowtimeIdsReply.newBuilder()
                        .setSuccess(true)
                        .setMessage("No showtime provided")
                        .setHasActiveBooking(false)
                        .build());
                responseObserver.onCompleted();
                return;
            }

            boolean hasActiveBooking = bookingRepository.existsByShowtimeIdInAndIsDeletedFalseAndBookingStatusIn(
                    showtimeIds,
                    EnumSet.of(BookingStatus.PENDING, BookingStatus.RESERVED, BookingStatus.CONFIRMED));

            responseObserver.onNext(HasActiveBookingByShowtimeIdsReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("Active booking check completed")
                    .setHasActiveBooking(hasActiveBooking)
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(HasActiveBookingByShowtimeIdsReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ex.getErrorCode().name())
                    .setMessage(ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while checking active bookings by showtime list", ex);
            responseObserver.onNext(HasActiveBookingByShowtimeIdsReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }
}
