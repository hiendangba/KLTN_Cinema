package com.cinema.booking_service.grpc;

import com.cinema.booking_service.entity.Booking;
import com.cinema.booking_service.enums.BookingStatus;
import com.cinema.booking_service.enums.PaymentStatus;
import com.cinema.booking_service.repository.BookingRepository;
import com.cinema.booking_service.repository.BookingSeatItemRepository;
import com.cinema.booking_service.services.SeatLockService;
import com.cinema.grpc.booking.CheckUserEligibleForReviewReply;
import com.cinema.grpc.booking.CheckUserEligibleForReviewRequest;
import com.cinema.grpc.booking.GetSeatRuntimeStatesReply;
import com.cinema.grpc.booking.GetSeatRuntimeStatesRequest;
import com.cinema.grpc.booking.ConfirmBookingPaymentReply;
import com.cinema.grpc.booking.ConfirmBookingPaymentRequest;
import com.cinema.grpc.booking.UpsertBookingPromotionSnapshotReply;
import com.cinema.grpc.booking.UpsertBookingPromotionSnapshotRequest;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingInternalGrpcServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingSeatItemRepository bookingSeatItemRepository;

    @Mock
    private SeatLockService seatLockService;

    @InjectMocks
    private BookingInternalGrpcService grpcService;

    @Test
    void confirmBookingPayment_shouldUsePayableAmountWhenPromotionApplied() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = buildBooking(bookingId);
        booking.setPromotionDiscountAmount(BigDecimal.valueOf(30000));
        booking.setPayableAmount(BigDecimal.valueOf(270000));

        when(bookingRepository.findLockedByIdAndIsDeletedFalse(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CapturingObserver<ConfirmBookingPaymentReply> observer = new CapturingObserver<>();
        grpcService.confirmBookingPayment(
                ConfirmBookingPaymentRequest.newBuilder()
                        .setBookingId(bookingId.toString())
                        .setTransactionAmount("270000")
                        .setPaymentMethod("MOMO_QR")
                        .setProviderRef("provider-ref")
                        .setOrderInvoiceNumber("PAY-001")
                        .build(),
                observer);

        assertTrue(observer.completed);
        assertTrue(observer.value.getSuccess());
        assertEquals("Booking payment confirmed successfully", observer.value.getMessage());
        assertEquals(PaymentStatus.PAID, booking.getPaymentStatus());
        assertEquals(BookingStatus.CONFIRMED, booking.getBookingStatus());
        verify(bookingRepository).save(booking);
        verify(seatLockService).releaseSeats(any(UUID.class), anyList());
    }

    @Test
    void confirmBookingPayment_shouldRejectGrossAmountWhenPromotionApplied() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = buildBooking(bookingId);
        booking.setPromotionDiscountAmount(BigDecimal.valueOf(30000));
        booking.setPayableAmount(BigDecimal.valueOf(270000));

        when(bookingRepository.findLockedByIdAndIsDeletedFalse(bookingId)).thenReturn(Optional.of(booking));

        CapturingObserver<ConfirmBookingPaymentReply> observer = new CapturingObserver<>();
        grpcService.confirmBookingPayment(
                ConfirmBookingPaymentRequest.newBuilder()
                        .setBookingId(bookingId.toString())
                        .setTransactionAmount("300000")
                        .setPaymentMethod("MOMO_QR")
                        .setProviderRef("provider-ref")
                        .setOrderInvoiceNumber("PAY-001")
                        .build(),
                observer);

        assertTrue(observer.completed);
        assertFalse(observer.value.getSuccess());
        assertEquals("BAD_REQUEST", observer.value.getErrorKey());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void confirmBookingPayment_shouldRejectWhenReservationExpired() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = buildBooking(bookingId);
        booking.setReservedUntil(LocalDateTime.now().minusMinutes(1));

        when(bookingRepository.findLockedByIdAndIsDeletedFalse(bookingId)).thenReturn(Optional.of(booking));

        CapturingObserver<ConfirmBookingPaymentReply> observer = new CapturingObserver<>();
        grpcService.confirmBookingPayment(
                ConfirmBookingPaymentRequest.newBuilder()
                        .setBookingId(bookingId.toString())
                        .setTransactionAmount("300000")
                        .setPaymentMethod("MOMO_QR")
                        .setProviderRef("provider-ref")
                        .setOrderInvoiceNumber("PAY-002")
                        .build(),
                observer);

        assertTrue(observer.completed);
        assertFalse(observer.value.getSuccess());
        assertEquals("BOOKING_EXPIRED", observer.value.getErrorKey());
        verify(bookingRepository, never()).save(any());
        verify(seatLockService, never()).releaseSeats(any(UUID.class), anyList());
    }

    @Test
    void upsertBookingPromotionSnapshot_shouldPersistPromoSnapshot() {
        UUID bookingId = UUID.randomUUID();
        UUID promotionId = UUID.randomUUID();
        Booking booking = buildBooking(bookingId);

        when(bookingRepository.findLockedByIdAndIsDeletedFalse(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CapturingObserver<UpsertBookingPromotionSnapshotReply> observer = new CapturingObserver<>();
        grpcService.upsertBookingPromotionSnapshot(
                UpsertBookingPromotionSnapshotRequest.newBuilder()
                        .setBookingId(bookingId.toString())
                        .setPromotionId(promotionId.toString())
                        .setPromotionCode("SALE10")
                        .setPromotionName("Sale 10%")
                        .setPromotionDiscountAmount("30000")
                        .setPayableAmount("270000")
                        .build(),
                observer);

        assertTrue(observer.completed);
        assertTrue(observer.value.getSuccess());
        assertEquals(promotionId, booking.getPromotionId());
        assertEquals("SALE10", booking.getPromotionCode());
        assertEquals("Sale 10%", booking.getPromotionName());
        assertEquals(0, booking.getPromotionDiscountAmount().compareTo(BigDecimal.valueOf(30000)));
        assertEquals(0, booking.getPayableAmount().compareTo(BigDecimal.valueOf(270000)));
        verify(bookingRepository).save(booking);
    }

    @Test
    void getSeatRuntimeStates_shouldReturnAvailableWhenReservationExpired() {
        UUID showtimeId = UUID.randomUUID();
        Booking booking = buildBooking(UUID.randomUUID());
        booking.setShowtimeId(showtimeId);
        booking.setReservedUntil(LocalDateTime.now().minusMinutes(1));

        when(bookingSeatItemRepository.findActiveLockedSeatCodesByShowtimeAndStatuses(
                any(UUID.class),
                anyList(),
                anyList(),
                any(LocalDateTime.class))).thenReturn(List.of());
        when(bookingSeatItemRepository.findSeatCodesByShowtimeAndStatuses(
                any(UUID.class),
                anyList(),
                anyList())).thenReturn(List.of());

        CapturingObserver<GetSeatRuntimeStatesReply> observer = new CapturingObserver<>();
        grpcService.getSeatRuntimeStates(
                GetSeatRuntimeStatesRequest.newBuilder()
                        .setShowtimeId(showtimeId.toString())
                        .addSeatCodes("F7")
                        .build(),
                observer);

        assertTrue(observer.completed);
        assertTrue(observer.value.getSuccess());
        assertEquals("AVAILABLE", observer.value.getSeatStatesList().get(0).getState());
        verify(bookingSeatItemRepository).findActiveLockedSeatCodesByShowtimeAndStatuses(
                eq(showtimeId),
                eq(List.of("F7")),
                eq(java.util.EnumSet.of(BookingStatus.RESERVED, BookingStatus.PENDING)),
                any(LocalDateTime.class));
    }

    @Test
    void checkUserEligibleForReview_shouldReturnTrueWhenPaymentConfirmedAndShowtimeEnded() {
        UUID userId = UUID.randomUUID();
        UUID filmId = UUID.randomUUID();
        Booking booking = buildBooking(UUID.randomUUID());
        booking.setUserId(userId);
        booking.setFilmId(filmId);
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setPaymentStatus(PaymentStatus.PAID);
        booking.setShowtimeEndDateTime(LocalDateTime.now().minusMinutes(1));

        when(bookingRepository.findAllByUserIdAndFilmIdAndIsDeletedFalseAndBookingStatusAndPaymentStatus(
                eq(userId),
                eq(filmId),
                eq(BookingStatus.CONFIRMED),
                eq(PaymentStatus.PAID))).thenReturn(List.of(booking));

        CapturingObserver<CheckUserEligibleForReviewReply> observer = new CapturingObserver<>();
        grpcService.checkUserEligibleForReview(
                CheckUserEligibleForReviewRequest.newBuilder()
                        .setUserId(userId.toString())
                        .setFilmId(filmId.toString())
                        .build(),
                observer);

        assertTrue(observer.completed);
        assertTrue(observer.value.getSuccess());
        assertTrue(observer.value.getEligible());
    }

    @Test
    void checkUserEligibleForReview_shouldReturnFalseWhenShowtimeNotEnded() {
        UUID userId = UUID.randomUUID();
        UUID filmId = UUID.randomUUID();
        Booking booking = buildBooking(UUID.randomUUID());
        booking.setUserId(userId);
        booking.setFilmId(filmId);
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setPaymentStatus(PaymentStatus.PAID);
        booking.setShowtimeEndDateTime(LocalDateTime.now().plusMinutes(1));

        when(bookingRepository.findAllByUserIdAndFilmIdAndIsDeletedFalseAndBookingStatusAndPaymentStatus(
                eq(userId),
                eq(filmId),
                eq(BookingStatus.CONFIRMED),
                eq(PaymentStatus.PAID))).thenReturn(List.of(booking));

        CapturingObserver<CheckUserEligibleForReviewReply> observer = new CapturingObserver<>();
        grpcService.checkUserEligibleForReview(
                CheckUserEligibleForReviewRequest.newBuilder()
                        .setUserId(userId.toString())
                        .setFilmId(filmId.toString())
                        .build(),
                observer);

        assertTrue(observer.completed);
        assertTrue(observer.value.getSuccess());
        assertFalse(observer.value.getEligible());
    }

    private Booking buildBooking(UUID bookingId) {
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setShowtimeId(UUID.randomUUID());
        booking.setCinemaId(UUID.randomUUID());
        booking.setBookingStatus(BookingStatus.RESERVED);
        booking.setPaymentStatus(PaymentStatus.UNPAID);
        booking.setFinalAmount(BigDecimal.valueOf(300000));
        booking.setPromotionDiscountAmount(BigDecimal.ZERO);
        booking.setPayableAmount(BigDecimal.valueOf(300000));
        booking.setIsDeleted(false);
        booking.setReservedUntil(LocalDateTime.now().plusMinutes(5));
        booking.setShowtimeEndDateTime(LocalDateTime.now().plusHours(2));
        return booking;
    }

    private static class CapturingObserver<T> implements StreamObserver<T> {
        private T value;
        private boolean completed;

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable t) {
            throw new AssertionError(t);
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }
}
