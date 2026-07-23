package com.cinema.showtime_service.dto.request;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.showtime_service.entity.PricingPolicy;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public enum PricingPolicyField {
    ID("id", UUID.class),
    NAME("name", String.class),
    CINEMA_ID("cinemaId", UUID.class),
    IS_DELETED("isDeleted", Boolean.class),
    TIME_CREATED("timeCreated", LocalDateTime.class),
    TIME_UPDATED("timeUpdated", LocalDateTime.class);

    private final String entityField;
    private final Class<?> dataType;

    PricingPolicyField(String entityField, Class<?> dataType) {
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
            if (dataType == Boolean.class) {
                if (!"true".equalsIgnoreCase(normalizedValue) && !"false".equalsIgnoreCase(normalizedValue)) {
                    throw new IllegalArgumentException("Boolean value must be true or false");
                }
                return Boolean.parseBoolean(normalizedValue);
            }
            if (dataType == UUID.class) {
                return UUID.fromString(normalizedValue);
            }
            if (dataType == LocalDateTime.class) {
                return LocalDateTime.parse(normalizedValue);
            }
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }

        throw new BusinessException(ErrorCode.UN_SUPPORTED_FIELD_TYPE);
    }

    public Comparable<?> getFieldValue(PricingPolicy pricingPolicy) {
        if (pricingPolicy == null) {
            return null;
        }
        return switch (this) {
            case ID -> pricingPolicy.getId();
            case NAME -> pricingPolicy.getName();
            case CINEMA_ID -> pricingPolicy.getCinemaId();
            case IS_DELETED -> pricingPolicy.getIsDeleted();
            case TIME_CREATED -> pricingPolicy.getTimeCreated();
            case TIME_UPDATED -> pricingPolicy.getTimeUpdated();
        };
    }
}
