package com.cinema.booking_service.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class BookingProductItemResponse {
    private UUID id;
    private UUID productId;
    private Integer quantity;
    private String productNameSnapshot;
    private BigDecimal unitPriceSnapshot;
    private BigDecimal lineTotal;
}

