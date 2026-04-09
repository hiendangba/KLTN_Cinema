package com.cinema.showtime_service.dto.request;

import com.cinema.Enum.ShowTimeEnum;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateShowTimeRequest {
    @NotNull(message = "pricingPolicyId không được để trống")
    UUID pricingPolicyId;

    @NotNull(message = "Thời gian bắt đầu không được để trống")
    @Future(message = "Thời gian bắt đầu phải ở tương lai")
    LocalDateTime startDateTime;

    @NotNull(message = "Thời gian kết thúc không được để trống")
    @Future(message = "Thời gian kết thúc phải ở tương lai")
    LocalDateTime endDateTime;

    @Enumerated
    @NotNull(message = "Trạng thái không được để trống")
    ShowTimeEnum.ShowTimeStatus status;
}
