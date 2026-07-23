package com.cinema.messaging;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerRankSettlementEvent(
        UUID eventId,
        UUID paymentTransactionId,
        UUID bookingId,
        UUID userId,
        BigDecimal settlementAmount,
        String settlementType,
        String source,
        LocalDateTime occurredAt) {
}
