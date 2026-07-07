package com.cinema.user_service.dto.response;

import com.cinema.user_service.enums.CustomerRankStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record CustomerRankResponse(
        UUID id,
        String code,
        String name,
        String description,
        BigDecimal minLifetimeAmount,
        BigDecimal earningAmountUnit,
        BigDecimal earningPointsPerUnit,
        Integer level,
        CustomerRankStatus status,
        LocalDateTime timeCreated,
        LocalDateTime timeUpdated) {
}
