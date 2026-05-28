package com.cinema.booking_service.grpc;

import com.cinema.booking_service.enums.BookingStatus;
import com.cinema.booking_service.enums.PaymentStatus;
import com.cinema.booking_service.repository.BookingRepository;
import com.cinema.booking_service.repository.BookingSeatItemRepository;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.booking.BookingInternalServiceGrpc;
import com.cinema.grpc.booking.BookingPaymentContextPayload;
import com.cinema.grpc.booking.CheckShowtimeBookedReply;
import com.cinema.grpc.booking.CheckShowtimeBookedRequest;
import com.cinema.grpc.booking.ConfirmBookingPaymentReply;
import com.cinema.grpc.booking.ConfirmBookingPaymentRequest;
import com.cinema.grpc.booking.GetSeatRuntimeStatesReply;
import com.cinema.grpc.booking.GetSeatRuntimeStatesRequest;
import com.cinema.grpc.booking.GetBookingPaymentContextReply;
import com.cinema.grpc.booking.GetBookingPaymentContextRequest;
import com.cinema.grpc.booking.HasActiveBookingByShowtimeIdsReply;
import com.cinema.grpc.booking.HasActiveBookingByShowtimeIdsRequest;
import com.cinema.grpc.booking.SeatRuntimeStatePayload;
import com.cinema.booking_service.entity.Booking;
import com.cinema.booking_service.services.SeatLockService;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final SeatLockService seatLockService;

    @Override
    public void getBookingPaymentContext(GetBookingPaymentContextRequest request,
                                         StreamObserver<GetBookingPaymentContextReply> responseObserver) {
        UUID bookingId;
        try {
            bookingId = UUID.fromString(request.getBookingId());
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(buildPaymentContextError(ErrorCode.INVALID_FORMAT));
            responseObserver.onCompleted();
            return;
        }

        try {
            Booking booking = bookingRepository.findByIdAndIsDeletedFalse(bookingId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
            responseObserver.onNext(GetBookingPaymentContextReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("Booking payment context fetched successfully")
                    .setBooking(toPaymentContextPayload(booking))
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(GetBookingPaymentContextReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ex.getErrorCode().name())
                    .setMessage(ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while fetching booking payment context", ex);
            responseObserver.onNext(GetBookingPaymentContextReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    @Transactional
    public void confirmBookingPayment(ConfirmBookingPaymentRequest request,
                                      StreamObserver<ConfirmBookingPaymentReply> responseObserver) {
        UUID bookingId;
        try {
            bookingId = UUID.fromString(request.getBookingId());
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(ConfirmBookingPaymentReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INVALID_FORMAT.name())
                    .setMessage(ErrorCode.INVALID_FORMAT.getMessage())
                    .build());
            responseObserver.onCompleted();
            return;
        }

        BigDecimal transactionAmount;
        try {
            transactionAmount = new BigDecimal(request.getTransactionAmount().trim());
        } catch (Exception ex) {
            responseObserver.onNext(ConfirmBookingPaymentReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INVALID_FORMAT.name())
                    .setMessage(ErrorCode.INVALID_FORMAT.getMessage())
                    .build());
            responseObserver.onCompleted();
            return;
        }

        try {
            Booking booking = bookingRepository.findLockedByIdAndIsDeletedFalse(bookingId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

            if (booking.getBookingStatus() == BookingStatus.EXPIRED) {
                throw new BusinessException(ErrorCode.BOOKING_EXPIRED);
            }
            if (booking.getBookingStatus() == BookingStatus.CANCELLED) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            if (booking.getBookingStatus() == BookingStatus.CONFIRMED
                    && booking.getPaymentStatus() == PaymentStatus.PAID) {
                responseObserver.onNext(ConfirmBookingPaymentReply.newBuilder()
                        .setSuccess(true)
                        .setMessage("Booking payment already confirmed")
                        .setBooking(toPaymentContextPayload(booking))
                        .build());
                responseObserver.onCompleted();
                return;
            }

            BigDecimal expectedAmount = booking.getFinalAmount() == null
                    ? BigDecimal.ZERO
                    : booking.getFinalAmount().setScale(0, RoundingMode.HALF_UP);
            BigDecimal normalizedTransactionAmount = transactionAmount.setScale(0, RoundingMode.HALF_UP);
            if (expectedAmount.compareTo(normalizedTransactionAmount) != 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }

            booking.setPaymentStatus(PaymentStatus.PAID);
            booking.setBookingStatus(BookingStatus.CONFIRMED);
            bookingRepository.save(booking);
            seatLockService.releaseSeats(booking.getShowtimeId(), extractSeatCodes(booking.getSeatItems()));

            responseObserver.onNext(ConfirmBookingPaymentReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("Booking payment confirmed successfully")
                    .setBooking(toPaymentContextPayload(booking))
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(ConfirmBookingPaymentReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ex.getErrorCode().name())
                    .setMessage(ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while confirming booking payment", ex);
            responseObserver.onNext(ConfirmBookingPaymentReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

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

    private GetBookingPaymentContextReply buildPaymentContextError(ErrorCode errorCode) {
        return GetBookingPaymentContextReply.newBuilder()
                .setSuccess(false)
                .setErrorKey(errorCode.name())
                .setMessage(errorCode.getMessage())
                .build();
    }

    private BookingPaymentContextPayload toPaymentContextPayload(Booking booking) {
        return BookingPaymentContextPayload.newBuilder()
                .setBookingId(booking.getId() == null ? "" : booking.getId().toString())
                .setShowtimeId(booking.getShowtimeId() == null ? "" : booking.getShowtimeId().toString())
                .setCinemaId(booking.getCinemaId() == null ? "" : booking.getCinemaId().toString())
                .setUserId(booking.getUserId() == null ? "" : booking.getUserId().toString())
                .setFinalAmount(booking.getFinalAmount() == null ? "0" : booking.getFinalAmount().toPlainString())
                .setReservedUntil(booking.getReservedUntil() == null ? "" : booking.getReservedUntil().toString())
                .setBookingStatus(booking.getBookingStatus() == null ? "" : booking.getBookingStatus().name())
                .setPaymentStatus(booking.getPaymentStatus() == null ? "" : booking.getPaymentStatus().name())
                .setTicketSubtotal(booking.getTicketSubtotal() == null ? "0" : booking.getTicketSubtotal().toPlainString())
                .setProductSubtotal(booking.getProductSubtotal() == null ? "0" : booking.getProductSubtotal().toPlainString())
                .build();
    }

    private List<String> extractSeatCodes(List<com.cinema.booking_service.entity.BookingSeatItem> seatItems) {
        if (seatItems == null || seatItems.isEmpty()) {
            return List.of();
        }
        return seatItems.stream()
                .map(com.cinema.booking_service.entity.BookingSeatItem::getSeatCode)
                .map(code -> code == null ? "" : code.trim().toUpperCase(Locale.ROOT))
                .toList();
    }
}
