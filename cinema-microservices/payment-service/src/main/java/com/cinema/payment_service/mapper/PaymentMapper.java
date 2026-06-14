package com.cinema.payment_service.mapper;

import com.cinema.payment_service.dto.response.PaymentSessionResponse;
import com.cinema.payment_service.dto.response.PromotionPreviewResponse;
import com.cinema.payment_service.dto.response.PromotionResponse;
import com.cinema.payment_service.entity.PaymentTransaction;
import com.cinema.payment_service.entity.Promotion;
import com.cinema.payment_service.enums.PromotionStatus;
import com.cinema.payment_service.support.PromotionQuote;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PaymentMapper {

    @Mapping(target = "discountValue", expression = "java(normalizeNullableAmount(promotion.getDiscountValue()))")
    @Mapping(target = "minOrderAmount", source = "promotion.minOrderAmount", qualifiedByName = "normalizeNullableAmount")
    @Mapping(target = "maxDiscountAmount", source = "promotion.maxDiscountAmount", qualifiedByName = "normalizeNullableAmount")
    @Mapping(target = "cinemaIds", expression = "java(normalizeUuidList(cinemaIds))")
    @Mapping(target = "filmIds", expression = "java(normalizeUuidList(filmIds))")
    @Mapping(target = "cinemaCount", expression = "java(normalizeUuidList(cinemaIds).size())")
    @Mapping(target = "filmCount", expression = "java(normalizeUuidList(filmIds).size())")
    @Mapping(target = "activeNow", expression = "java(isActiveNow(promotion))")
    PromotionResponse toPromotionResponse(Promotion promotion, Collection<UUID> cinemaIds, Collection<UUID> filmIds);

    @Mapping(target = "amount", source = "transaction.amount", qualifiedByName = "normalizeAmount")
    @Mapping(target = "ticketSubtotalSnapshot", source = "transaction.ticketSubtotalSnapshot", qualifiedByName = "normalizeNullableAmount")
    @Mapping(target = "productSubtotalSnapshot", source = "transaction.productSubtotalSnapshot", qualifiedByName = "normalizeNullableAmount")
    @Mapping(target = "checkoutFields", source = "checkoutFields")
    @Mapping(target = "qrCodeUrl", source = "qrCodeUrl")
    @Mapping(target = "completedByUserId", source = "transaction.completedByUserId")
    @Mapping(target = "completedByRole", source = "transaction.completedByRole")
    @Mapping(target = "refundAmount", source = "transaction.refundAmount", qualifiedByName = "normalizeNullableAmount")
    @Mapping(target = "promotionDiscountAmount", source = "transaction.promotionDiscountAmount", qualifiedByName = "normalizeNullableAmount")
    PaymentSessionResponse toPaymentSessionResponse(
            PaymentTransaction transaction,
            java.util.Map<String, String> checkoutFields,
            String qrCodeUrl);

    @Mapping(target = "promotionCode", source = "quote.promotionCode")
    @Mapping(target = "discountAmount", source = "quote.discountAmount", qualifiedByName = "normalizeAmount")
    @Mapping(target = "note", source = "quote.note")
    @Mapping(target = "originalAmount", source = "originalAmount", qualifiedByName = "normalizeAmount")
    @Mapping(target = "finalAmount", expression = "java(normalizeAmount(originalAmount).subtract(normalizeAmount(quote.discountAmount())))")
    PromotionPreviewResponse toPromotionPreviewResponse(PromotionQuote quote, BigDecimal originalAmount);

    default List<UUID> normalizeUuidList(Collection<UUID> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<UUID> normalized = values.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        return normalized.isEmpty() ? List.of() : normalized;
    }

    @Named("normalizeAmount")
    default BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(0, RoundingMode.HALF_UP);
        }
        return amount.setScale(0, RoundingMode.HALF_UP);
    }

    @Named("normalizeNullableAmount")
    default BigDecimal normalizeNullableAmount(BigDecimal amount) {
        return amount == null ? null : normalizeAmount(amount);
    }

    default boolean isActiveNow(Promotion promotion) {
        if (promotion == null || promotion.getStatus() != PromotionStatus.ACTIVE) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (promotion.getStartAt() != null && now.isBefore(promotion.getStartAt())) {
            return false;
        }
        if (promotion.getEndAt() != null && now.isAfter(promotion.getEndAt())) {
            return false;
        }
        return true;
    }
}
