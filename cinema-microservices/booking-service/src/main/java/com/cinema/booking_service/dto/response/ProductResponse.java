package com.cinema.booking_service.dto.response;

import com.cinema.booking_service.enums.ProductStatus;
import com.cinema.booking_service.enums.ProductType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ProductResponse {
    private UUID id;
    private UUID cinemaId;
    private String name;
    private ProductType type;
    private BigDecimal price;
    private String description;
    private String imageUrl;
    private ProductStatus status;
    private LocalDateTime timeCreated;
    private LocalDateTime timeUpdated;
}

