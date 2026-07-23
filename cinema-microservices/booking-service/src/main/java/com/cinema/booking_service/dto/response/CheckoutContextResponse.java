package com.cinema.booking_service.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CheckoutContextResponse {
    private BookingResponse booking;
    private PaymentSessionSnapshotResponse paymentSession;
    private boolean canPay;
    private long secondsToExpire;
}
