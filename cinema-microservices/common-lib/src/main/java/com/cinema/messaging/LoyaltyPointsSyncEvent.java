package com.cinema.messaging;

import java.time.LocalDateTime;
import java.util.UUID;

public record LoyaltyPointsSyncEvent(
        UUID eventId,
        UUID paymentTransactionId,
        UUID bookingId,
        UUID userId,
        long loyaltyPointsUsed,
        long loyaltyPointsEarned,
        String source,
        LocalDateTime occurredAt) {
}
