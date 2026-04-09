package com.cinema.showtime_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PricingPolicyUpdateRequest {
    @NotBlank(message = "Tên chính sách giá không được để trống")
    String name;

    @NotNull(message = "Giá STANDARD không được để trống")
    @PositiveOrZero(message = "Giá STANDARD phải lớn hơn hoặc bằng 0")
    Long standardPrice;

    @NotNull(message = "Giá VIP không được để trống")
    @PositiveOrZero(message = "Giá VIP phải lớn hơn hoặc bằng 0")
    Long vipPrice;

    @NotNull(message = "Giá COUPLE không được để trống")
    @PositiveOrZero(message = "Giá COUPLE phải lớn hơn hoặc bằng 0")
    Long couplePrice;
}
