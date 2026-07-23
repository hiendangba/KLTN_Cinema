package com.cinema.booking_service.dto.request;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Getter
public enum ShowtimePerformanceField {
    SHOWTIME_ID("showtimeId", UUID.class),
    CINEMA_ID("cinemaId", UUID.class),
    FILM_ID("filmId", UUID.class),
    HALL_ID("hallId", UUID.class),
    START_DATE_TIME("startDateTime", LocalDateTime.class),
    END_DATE_TIME("endDateTime", LocalDateTime.class),
    TOTAL_BOOKINGS("totalBookings", Long.class),
    TOTAL_SEATS_BOOKED("totalSeatsBooked", Long.class),
    TOTAL_SEAT_CAPACITY("totalSeatCapacity", Long.class),
    OCCUPANCY_RATE("occupancyRate", BigDecimal.class);

    private final String reportField;
    private final Class<?> dataType;

    ShowtimePerformanceField(String reportField, Class<?> dataType) {
        this.reportField = reportField;
        this.dataType = dataType;
    }

    public static Comparable<?> convertValue(String value, Class<?> dataType) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }

        String normalizedValue = value.trim();

        try {
            if (dataType == UUID.class) {
                return UUID.fromString(normalizedValue);
            } else if (dataType == LocalDateTime.class) {
                return LocalDateTime.parse(normalizedValue);
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
