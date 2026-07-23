package com.cinema.payment_service.dto.request;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Getter
public enum CinemaRevenueField {
    CINEMA_ID("cinemaId", UUID.class),
    CINEMA_NAME("cinemaName", String.class),
    TOTAL_TRANSACTIONS("totalTransactions", Long.class),
    PENDING_COUNT("pendingCount", Long.class),
    PAID_COUNT("paidCount", Long.class),
    FAILED_COUNT("failedCount", Long.class),
    EXPIRED_COUNT("expiredCount", Long.class),
    REFUND_PENDING_COUNT("refundPendingCount", Long.class),
    REFUNDED_COUNT("refundedCount", Long.class),
    TICKET_SUBTOTAL_AMOUNT("ticketSubtotalAmount", BigDecimal.class),
    PRODUCT_SUBTOTAL_AMOUNT("productSubtotalAmount", BigDecimal.class),
    PROMOTION_CODE("promotionCode", String.class),
    PROMOTION_NAME("promotionName", String.class),
    PROMOTION_DISCOUNT_AMOUNT("promotionDiscountAmount", BigDecimal.class),
    LOYALTY_POINTS_DISCOUNT_AMOUNT("loyaltyPointsDiscountAmount", BigDecimal.class),
    PAID_AMOUNT("paidAmount", BigDecimal.class),
    REFUNDED_AMOUNT("refundedAmount", BigDecimal.class),
    GROSS_AMOUNT("grossAmount", BigDecimal.class),
    NET_AMOUNT("netAmount", BigDecimal.class);

    private final String reportField;
    private final Class<?> dataType;

    CinemaRevenueField(String reportField, Class<?> dataType) {
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
