package com.cinema.payment_service.dto.response;

import com.cinema.payment_service.enums.PromotionDiscountType;
import com.cinema.payment_service.enums.PromotionStatus;
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
public class PromotionResponse {

    private UUID id;
    private String code;
    private String name;
    private String description;
    private PromotionDiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmount;
    private BigDecimal maxDiscountAmount;
    private CustomerRankSummaryResponse customerRank;
    private BigDecimal minCustomerLifetimeAmount;
    private Integer maxUsageCount;
    private Long usedCount;
    private Long remainingUsageCount;
    private PromotionStatus status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private UUID createdByUserId;
    private String createdByRole;
    private List<UUID> cinemaIds;
    private List<UUID> filmIds;
    private Integer cinemaCount;
    private Integer filmCount;
    private Boolean activeNow;
    private LocalDateTime timeCreated;
    private LocalDateTime timeUpdated;
}
