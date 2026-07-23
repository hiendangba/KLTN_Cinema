package com.cinema.user_service.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerRankSnapshot(
        UUID id,
        String code,
        String name,
        BigDecimal minLifetimeAmount,
        BigDecimal earningAmountUnit,
        BigDecimal earningPointsPerUnit,
        Integer level) {
}
