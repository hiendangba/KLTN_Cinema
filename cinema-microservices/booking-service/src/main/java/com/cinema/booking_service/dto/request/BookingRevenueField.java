package com.cinema.booking_service.dto.request;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Getter
public enum BookingRevenueField {
    CINEMA_ID("cinemaId", UUID.class),
    CINEMA_NAME("cinemaName", String.class),
    TOTAL_BOOKINGS("totalBookings", Long.class),
    PENDING_COUNT("pendingCount", Long.class),
    RESERVED_COUNT("reservedCount", Long.class),
    CONFIRMED_COUNT("confirmedCount", Long.class),
    TICKET_SUBTOTAL_AMOUNT("ticketSubtotalAmount", BigDecimal.class),
    PRODUCT_SUBTOTAL_AMOUNT("productSubtotalAmount", BigDecimal.class),
    GROSS_AMOUNT("grossAmount", BigDecimal.class),
    PROMOTION_DISCOUNT_AMOUNT("promotionDiscountAmount", BigDecimal.class),
    PAYABLE_AMOUNT("payableAmount", BigDecimal.class);

    private final String reportField;
    private final Class<?> dataType;

    BookingRevenueField(String reportField, Class<?> dataType) {
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
