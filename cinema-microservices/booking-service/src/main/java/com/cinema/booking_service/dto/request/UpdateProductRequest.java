package com.cinema.booking_service.dto.request;

import com.cinema.booking_service.enums.ProductStatus;
import com.cinema.booking_service.enums.ProductType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateProductRequest {
    @NotBlank(message = "name is required")
    @Size(max = 150, message = "name must be at most 150 characters")
    private String name;

    @NotNull(message = "type is required")
    private ProductType type;

    @NotNull(message = "price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "price must be greater than 0")
    private BigDecimal price;

    @Size(max = 3000, message = "description must be at most 3000 characters")
    private String description;

    @Size(max = 1000, message = "imageUrl must be at most 1000 characters")
    private String imageUrl;

    @NotNull(message = "status is required")
    private ProductStatus status;
}

