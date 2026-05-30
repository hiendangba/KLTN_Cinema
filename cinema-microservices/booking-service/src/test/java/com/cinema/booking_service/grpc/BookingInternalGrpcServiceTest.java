package com.cinema.booking_service.grpc;

import com.cinema.booking_service.entity.Booking;
import com.cinema.booking_service.enums.BookingStatus;
import com.cinema.booking_service.enums.PaymentStatus;
import com.cinema.booking_service.repository.BookingRepository;
import com.cinema.booking_service.repository.BookingSeatItemRepository;
import com.cinema.booking_service.services.SeatLockService;
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

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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
