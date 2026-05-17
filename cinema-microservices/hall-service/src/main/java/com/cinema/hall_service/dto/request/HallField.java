package com.cinema.hall_service.dto.request;

import com.cinema.Enum.HallEnum;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Getter
public enum HallField {
    ID("id", UUID.class),
    CINEMA_ID("cinemaId", UUID.class),
    NAME("name", String.class),
    STATUS("status", HallEnum.HallStatus.class),
    IS_DELETED("isDeleted", Boolean.class),
    TIME_CREATED("timeCreated", LocalDateTime.class),
    TIME_UPDATED("timeUpdated", LocalDateTime.class);

    private final String entityField;
    private final Class<?> dataType;

    HallField(String entityField, Class<?> dataType) {
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
            } else if (dataType == Integer.class) {
                return Integer.parseInt(normalizedValue);
            } else if (dataType == Boolean.class) {
                if (!"true".equalsIgnoreCase(normalizedValue) && !"false".equalsIgnoreCase(normalizedValue)) {
                    throw new IllegalArgumentException("Boolean value must be true or false");
                }
                return Boolean.parseBoolean(normalizedValue);
            } else if (dataType == UUID.class) {
                return UUID.fromString(normalizedValue);
            } else if (dataType == LocalDateTime.class) {
                return LocalDateTime.parse(normalizedValue);
            } else if (dataType == HallEnum.HallStatus.class) {
                return HallEnum.HallStatus.valueOf(normalizedValue.toUpperCase(Locale.ROOT));
            }
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }

        throw new BusinessException(ErrorCode.UN_SUPPORTED_FIELD_TYPE);
    }
}
