package com.cinema.payment_service.dto.request;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.payment_service.enums.PaymentTransactionStatus;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Getter
public enum PaymentSessionField {
    ID("id", UUID.class),
    BOOKING_ID("bookingId", UUID.class),
    STATUS("status", PaymentTransactionStatus.class),
    TIME_CREATED("timeCreated", LocalDateTime.class);

    private final String entityField;
    private final Class<?> dataType;

    PaymentSessionField(String entityField, Class<?> dataType) {
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
            } else if (dataType == UUID.class) {
                return UUID.fromString(normalizedValue);
            } else if (dataType == LocalDateTime.class) {
                return LocalDateTime.parse(normalizedValue);
            } else if (dataType == PaymentTransactionStatus.class) {
                return PaymentTransactionStatus.valueOf(normalizedValue.toUpperCase(Locale.ROOT));
            }
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }

        throw new BusinessException(ErrorCode.UN_SUPPORTED_FIELD_TYPE);
    }
}
