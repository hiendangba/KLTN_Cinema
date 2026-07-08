package com.cinema.payment_service.dto.request;

import com.cinema.payment_service.enums.PromotionDiscountType;
import com.cinema.payment_service.enums.PromotionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionUpsertRequest {

    @NotBlank(message = "Promotion code is required")
    @Size(max = 80, message = "Promotion code must not exceed 80 characters")
    private String code;

    @NotBlank(message = "Promotion name is required")
    @Size(max = 120, message = "Promotion name must not exceed 120 characters")
    private String name;

    @Size(max = 2000, message = "Promotion description must not exceed 2000 characters")
    private String description;

    @NotNull(message = "Discount type is required")
    private PromotionDiscountType discountType;

    @NotNull(message = "Discount value is required")
    private BigDecimal discountValue;

    private BigDecimal minOrderAmount;

    private BigDecimal maxDiscountAmount;

    private UUID minCustomerRankId;

    private Integer maxUsageCount;

    @NotNull(message = "Promotion start time is required")
    private LocalDateTime startAt;

    @NotNull(message = "Promotion end time is required")
    private LocalDateTime endAt;

    private PromotionStatus status;

    private List<UUID> cinemaIds;

    private List<UUID> filmIds;
}
