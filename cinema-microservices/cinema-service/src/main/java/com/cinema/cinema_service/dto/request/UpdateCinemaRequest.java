package com.cinema.cinema_service.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateCinemaRequest {

    @NotBlank(message = "Cinema name is required")
    String name;

    @NotBlank(message = "Address is required")
    String address;

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", inclusive = true, message = "Latitude must be >= -90")
    @DecimalMax(value = "90.0", inclusive = true, message = "Latitude must be <= 90")
    BigDecimal latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", inclusive = true, message = "Longitude must be >= -180")
    @DecimalMax(value = "180.0", inclusive = true, message = "Longitude must be <= 180")
    BigDecimal longitude;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^0(3|5|7|8|9)\\d{8}$", message = "Phone must be a valid Vietnamese 10-digit mobile number")
    String phone;

    @NotNull(message = "Open time is required")
    LocalTime openTime;

    @NotNull(message = "Close time is required")
    LocalTime closeTime;

    UUID managerId;
}
