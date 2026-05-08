package com.cinema.booking_service.dto.request;

import com.cinema.Enum.HallEnum;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CreateBookingRequest {
    @NotNull(message = "showtimeId is required")
    private UUID showtimeId;

    @NotNull(message = "cinemaId is required")
    private UUID cinemaId;

    @Valid
    @NotNull(message = "customerInfo is required")
    private CustomerInfo customerInfo;

    @Valid
    @NotEmpty(message = "seatItems must not be empty")
    private List<SeatItem> seatItems = new ArrayList<>();

    @Valid
    private List<ProductItem> productItems = new ArrayList<>();

    @Getter
    @Setter
    public static class CustomerInfo {
        @NotBlank(message = "fullName is required")
        @Size(max = 120, message = "fullName must be at most 120 characters")
        private String fullName;

        @NotBlank(message = "email is required")
        @Email(message = "email is invalid")
        @Size(max = 180, message = "email must be at most 180 characters")
        private String email;

        @NotBlank(message = "phone is required")
        @Pattern(regexp = "^[0-9+()\\-\\s]{8,30}$", message = "phone is invalid")
        private String phone;
    }

    @Getter
    @Setter
    public static class SeatItem {
        @NotBlank(message = "seatCode is required")
        @Size(max = 20, message = "seatCode must be at most 20 characters")
        private String seatCode;

        @NotNull(message = "seatType is required")
        private HallEnum.SeatType seatType;

        @NotNull(message = "seatPriceSnapshot is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "seatPriceSnapshot must be greater than 0")
        private BigDecimal seatPriceSnapshot;
    }

    @Getter
    @Setter
    public static class ProductItem {
        @NotNull(message = "productId is required")
        private UUID productId;

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        private Integer quantity;
    }
}
