package com.cinema.payment_service.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CustomerRankSummaryResponse(
        UUID id,
        String code,
        String name) {
}
