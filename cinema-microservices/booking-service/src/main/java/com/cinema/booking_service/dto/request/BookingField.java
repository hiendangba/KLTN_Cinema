package com.cinema.booking_service.dto.request;

import com.cinema.booking_service.enums.BookingStatus;
import com.cinema.booking_service.enums.PaymentStatus;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Getter
public enum BookingField {
    ID("id", UUID.class),
    SHOWTIME_ID("showtimeId", UUID.class),
    CINEMA_ID("cinemaId", UUID.class),
    USER_ID("userId", UUID.class),
    PAYMENT_STATUS("paymentStatus", PaymentStatus.class),
    BOOKING_STATUS("bookingStatus", BookingStatus.class),
    RESERVED_UNTIL("reservedUntil", LocalDateTime.class),
    TIME_CREATED("timeCreated", LocalDateTime.class),
    TIME_UPDATED("timeUpdated", LocalDateTime.class);

    private final String entityField;
    private final Class<?> dataType;

    BookingField(String entityField, Class<?> dataType) {
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
            } else if (dataType == BookingStatus.class) {
                return BookingStatus.valueOf(normalizedValue.toUpperCase(Locale.ROOT));
            } else if (dataType == PaymentStatus.class) {
                return PaymentStatus.valueOf(normalizedValue.toUpperCase(Locale.ROOT));
            }
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }

        throw new BusinessException(ErrorCode.UN_SUPPORTED_FIELD_TYPE);
    }
}
