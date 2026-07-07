package com.cinema.payment_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.payment.GetPaymentSessionByBookingIdReply;
import com.cinema.grpc.payment.GetPaymentSessionByBookingIdRequest;
import com.cinema.payment_service.dto.response.PaymentSessionResponse;
import com.cinema.payment_service.enums.PaymentTransactionStatus;
import com.cinema.payment_service.services.PaymentSessionService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentInternalGrpcServiceTest {

    @Mock
    private PaymentSessionService paymentSessionService;

    @InjectMocks
    private PaymentInternalGrpcService grpcService;

    @Test
    void getPaymentSessionByBookingId_shouldReturnSessionPayload() {
        UUID bookingId = UUID.randomUUID();
        UUID requesterUserId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID showtimeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        PaymentSessionResponse session = PaymentSessionResponse.builder()
                .id(sessionId)
                .bookingId(bookingId)
                .showtimeId(showtimeId)
                .userId(userId)
                .paymentMethod("MOMO_QR")
                .orderInvoiceNumber("PAY-001")
                .providerRef("provider-ref")
                .payUrl("https://momo.example.com/pay")
                .status(PaymentTransactionStatus.PENDING)
                .amount(BigDecimal.valueOf(180000))
                .promotionCode("SALE10")
                .promotionName("Sale 10%")
                .promotionDiscountAmount(BigDecimal.valueOf(18000))
                .loyaltyPointsUsed(2000L)
                .loyaltyPointsEarned(120L)
                .expiresAt(LocalDateTime.of(2026, 7, 7, 10, 0))
                .paidAt(null)
                .expiredAt(null)
                .failureReason("")
                .build();

        when(paymentSessionService.getSession(bookingId, requesterUserId, "CUSTOMER")).thenReturn(session);

        CapturingObserver<GetPaymentSessionByBookingIdReply> observer = new CapturingObserver<>();
        grpcService.getPaymentSessionByBookingId(
                GetPaymentSessionByBookingIdRequest.newBuilder()
                        .setBookingId(bookingId.toString())
                        .setRequesterUserId(requesterUserId.toString())
                        .setRequesterRole("CUSTOMER")
                        .build(),
                observer);

        assertTrue(observer.completed);
        assertTrue(observer.value.getSuccess());
        assertEquals(sessionId.toString(), observer.value.getSession().getId());
        assertEquals(bookingId.toString(), observer.value.getSession().getBookingId());
        assertEquals("PENDING", observer.value.getSession().getStatus());
        assertEquals("180000", observer.value.getSession().getAmount());
        assertEquals(2000L, observer.value.getSession().getLoyaltyPointsUsed());
        assertEquals(120L, observer.value.getSession().getLoyaltyPointsEarned());
    }

    @Test
    void getPaymentSessionByBookingId_shouldReturnBusinessError() {
        UUID bookingId = UUID.randomUUID();
        UUID requesterUserId = UUID.randomUUID();

        when(paymentSessionService.getSession(bookingId, requesterUserId, "CUSTOMER"))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        CapturingObserver<GetPaymentSessionByBookingIdReply> observer = new CapturingObserver<>();
        grpcService.getPaymentSessionByBookingId(
                GetPaymentSessionByBookingIdRequest.newBuilder()
                        .setBookingId(bookingId.toString())
                        .setRequesterUserId(requesterUserId.toString())
                        .setRequesterRole("CUSTOMER")
                        .build(),
                observer);

        assertTrue(observer.completed);
        assertFalse(observer.value.getSuccess());
        assertEquals("FORBIDDEN", observer.value.getErrorKey());
    }

    @Test
    void getPaymentSessionByBookingId_shouldRejectInvalidUuid() {
        CapturingObserver<GetPaymentSessionByBookingIdReply> observer = new CapturingObserver<>();
        grpcService.getPaymentSessionByBookingId(
                GetPaymentSessionByBookingIdRequest.newBuilder()
                        .setBookingId("not-a-uuid")
                        .setRequesterUserId("still-not-a-uuid")
                        .setRequesterRole("CUSTOMER")
                        .build(),
                observer);

        assertTrue(observer.completed);
        assertFalse(observer.value.getSuccess());
        assertEquals("INVALID_FORMAT", observer.value.getErrorKey());
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
