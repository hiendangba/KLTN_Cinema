package com.cinema.payment_service.dto.request;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Getter
public enum FilmRevenueField {
    FILM_ID("filmId", UUID.class),
    FILM_NAME("filmName", String.class),
    CINEMA_COUNT("cinemaCount", Long.class),
    TOTAL_TRANSACTIONS("totalTransactions", Long.class),
    PAID_COUNT("paidCount", Long.class),
    REFUNDED_COUNT("refundedCount", Long.class),
    PAID_AMOUNT("paidAmount", BigDecimal.class);

    private final String reportField;
    private final Class<?> dataType;

    FilmRevenueField(String reportField, Class<?> dataType) {
        this.reportField = reportField;
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
            } else if (dataType == Long.class) {
                return Long.parseLong(normalizedValue);
            } else if (dataType == BigDecimal.class) {
                return new BigDecimal(normalizedValue);
            }
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }

        throw new BusinessException(ErrorCode.UN_SUPPORTED_FIELD_TYPE);
    }

    public static String normalizeDirection(String direction) {
        return direction == null ? "ASC" : direction.trim().toUpperCase(Locale.ROOT);
    }
}
