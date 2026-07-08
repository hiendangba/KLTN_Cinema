package com.cinema.payment_service.dto.request;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.payment_service.entity.Promotion;
import com.cinema.payment_service.enums.PromotionDiscountType;
import com.cinema.payment_service.enums.PromotionStatus;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Getter
public enum PromotionField {
    ID("id", UUID.class),
    CINEMA_ID("cinemaId", UUID.class),
    FILM_ID("filmId", UUID.class),
    CODE("code", String.class),
    NAME("name", String.class),
    DISCOUNT_TYPE("discountType", PromotionDiscountType.class),
    DISCOUNT_VALUE("discountValue", BigDecimal.class),
    MIN_ORDER_AMOUNT("minOrderAmount", BigDecimal.class),
    MAX_DISCOUNT_AMOUNT("maxDiscountAmount", BigDecimal.class),
    MIN_CUSTOMER_RANK_ID("minCustomerRankId", UUID.class),
    MIN_CUSTOMER_LIFETIME_AMOUNT("minCustomerLifetimeAmount", BigDecimal.class),
    MAX_USAGE_COUNT("maxUsageCount", Integer.class),
    STATUS("status", PromotionStatus.class),
    START_AT("startAt", LocalDateTime.class),
    END_AT("endAt", LocalDateTime.class),
    CREATED_BY_USER_ID("createdByUserId", UUID.class),
    CREATED_BY_ROLE("createdByRole", String.class),
    TIME_CREATED("timeCreated", LocalDateTime.class),
    TIME_UPDATED("timeUpdated", LocalDateTime.class);

    private final String entityField;
    private final Class<?> dataType;

    PromotionField(String entityField, Class<?> dataType) {
        this.entityField = entityField;
        this.dataType = dataType;
    }

    public static Comparable<?> convertValue(String value, Class<?> dataType) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }

        String normalizedValue = value.trim();
        try {
            if (dataType == String.class) {
                return normalizedValue;
            }
            if (dataType == BigDecimal.class) {
                return new BigDecimal(normalizedValue);
            }
            if (dataType == UUID.class) {
                return UUID.fromString(normalizedValue);
            }
            if (dataType == LocalDateTime.class) {
                return LocalDateTime.parse(normalizedValue);
            }
            if (dataType == PromotionDiscountType.class) {
                return PromotionDiscountType.valueOf(normalizedValue.toUpperCase(Locale.ROOT));
            }
            if (dataType == PromotionStatus.class) {
                return PromotionStatus.valueOf(normalizedValue.toUpperCase(Locale.ROOT));
            }
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }
        throw new BusinessException(ErrorCode.UN_SUPPORTED_FIELD_TYPE);
    }

    public Comparable<?> getFieldValue(Promotion promotion) {
        if (promotion == null) {
            return null;
        }
        return switch (this) {
            case ID -> promotion.getId();
            case CINEMA_ID -> null;
            case FILM_ID -> null;
            case CODE -> promotion.getCode();
            case NAME -> promotion.getName();
            case DISCOUNT_TYPE -> promotion.getDiscountType();
            case DISCOUNT_VALUE -> promotion.getDiscountValue();
            case MIN_ORDER_AMOUNT -> promotion.getMinOrderAmount();
            case MAX_DISCOUNT_AMOUNT -> promotion.getMaxDiscountAmount();
            case MIN_CUSTOMER_RANK_ID -> promotion.getMinCustomerRankId();
            case MIN_CUSTOMER_LIFETIME_AMOUNT -> promotion.getMinCustomerLifetimeAmount();
            case MAX_USAGE_COUNT -> promotion.getMaxUsageCount();
            case STATUS -> promotion.getStatus();
            case START_AT -> promotion.getStartAt();
            case END_AT -> promotion.getEndAt();
            case CREATED_BY_USER_ID -> promotion.getCreatedByUserId();
            case CREATED_BY_ROLE -> promotion.getCreatedByRole();
            case TIME_CREATED -> promotion.getTimeCreated();
            case TIME_UPDATED -> promotion.getTimeUpdated();
        };
    }
}
