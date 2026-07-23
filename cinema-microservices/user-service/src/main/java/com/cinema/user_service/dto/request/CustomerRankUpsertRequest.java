package com.cinema.user_service.dto.request;

import com.cinema.user_service.enums.CustomerRankStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CustomerRankUpsertRequest {
    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "name is required")
    private String name;

    private String description;

    @NotNull(message = "minLifetimeAmount is required")
    @PositiveOrZero(message = "minLifetimeAmount must not be negative")
    private BigDecimal minLifetimeAmount;

    @NotNull(message = "earningAmountUnit is required")
    @Positive(message = "earningAmountUnit must be positive")
    private BigDecimal earningAmountUnit;

    @NotNull(message = "earningPointsPerUnit is required")
    @Positive(message = "earningPointsPerUnit must be positive")
    private BigDecimal earningPointsPerUnit;

    @NotNull(message = "level is required")
    @Positive(message = "level must be positive")
    private Integer level;

    private CustomerRankStatus status;
}
