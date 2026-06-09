package com.cinema.payment_service.support;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.payment_service.dto.request.PromotionPreviewRequest;
import com.cinema.payment_service.dto.response.PromotionPreviewResponse;
import com.cinema.payment_service.entity.Promotion;
import com.cinema.payment_service.entity.PromotionCinema;
import com.cinema.payment_service.entity.PromotionFilm;
import com.cinema.payment_service.enums.PromotionDiscountType;
import com.cinema.payment_service.enums.PromotionStatus;
import com.cinema.payment_service.grpc.BookingGrpcClient;
import com.cinema.payment_service.mapper.PaymentMapper;
import com.cinema.payment_service.repository.PromotionCinemaRepository;
import com.cinema.payment_service.repository.PromotionFilmRepository;
import com.cinema.payment_service.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PromotionEngine {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(0, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PromotionRepository promotionRepository;
    private final PromotionCinemaRepository promotionCinemaRepository;
    private final PromotionFilmRepository promotionFilmRepository;
    private final BookingGrpcClient bookingGrpcClient;
    private final PaymentMapper paymentMapper;

    public PromotionPreviewResponse previewPromotion(PromotionPreviewRequest request, UUID requesterUserId) {
        if (request == null || requesterUserId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        BookingGrpcClient.BookingPaymentContext bookingContext = null;
        BigDecimal baseAmount = normalizeAmount(request.getOrderAmount());
        if (request.getBookingId() != null) {
            bookingContext = bookingGrpcClient.getBookingPaymentContext(request.getBookingId());
            ensureRequesterOwnsBooking(requesterUserId, bookingContext.userId());
            if (baseAmount.compareTo(ZERO) <= 0) {
                baseAmount = normalizeAmount(bookingContext.finalAmount());
            }
        }

        if (baseAmount.compareTo(ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        PromotionQuote quote = resolvePromotion(
                normalizePromotionCode(request.getPromotionCode()),
                baseAmount,
                bookingContext,
                requesterUserId,
                false);

        return paymentMapper.toPromotionPreviewResponse(quote, baseAmount);
    }

    public PromotionQuote resolvePromotionForCheckout(
            String promotionCode,
            BigDecimal baseAmount,
            BookingGrpcClient.BookingPaymentContext bookingContext,
            UUID requesterUserId) {
        return resolvePromotion(
                normalizePromotionCode(promotionCode),
                normalizeAmount(baseAmount),
                bookingContext,
                requesterUserId,
                true);
    }

    private PromotionQuote resolvePromotion(
            String promotionCode,
            BigDecimal baseAmount,
            BookingGrpcClient.BookingPaymentContext bookingContext,
            UUID requesterUserId,
            boolean strict) {
        if (!StringUtils.hasText(promotionCode)) {
            return new PromotionQuote("", "", ZERO, "No promotion code", null);
        }

        Promotion promotion = promotionRepository.findByCodeIgnoreCaseAndIsDeletedFalse(promotionCode)
                .orElse(null);
        if (promotion == null) {
            if (strict) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            return new PromotionQuote(promotionCode, "", ZERO, "Promotion code is not supported", null);
        }

        if (!isPromotionActiveNow(promotion)) {
            if (strict) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            return new PromotionQuote(
                    promotion.getCode(),
                    promotion.getName(),
                    ZERO,
                    "Promotion is inactive or expired",
                    promotion.getId());
        }

        if (bookingContext != null) {
            ensureRequesterOwnsBooking(requesterUserId, bookingContext.userId());
            if (!matchesScope(promotion, bookingContext.cinemaId(), bookingContext.filmId())) {
                if (strict) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST);
                }
                return new PromotionQuote(
                        promotion.getCode(),
                        promotion.getName(),
                        ZERO,
                        "Promotion is not applicable to this booking",
                        promotion.getId());
            }
        } else if (hasCinemaOrFilmScope(promotion)) {
            if (strict) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            return new PromotionQuote(
                    promotion.getCode(),
                    promotion.getName(),
                    ZERO,
                    "Promotion requires booking context",
                    promotion.getId());
        }

        if (promotion.getMinOrderAmount() != null
                && normalizeAmount(baseAmount).compareTo(normalizeAmount(promotion.getMinOrderAmount())) < 0) {
            if (strict) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            return new PromotionQuote(
                    promotion.getCode(),
                    promotion.getName(),
                    ZERO,
                    "Min order is " + normalizeAmount(promotion.getMinOrderAmount()),
                    promotion.getId());
        }

        BigDecimal discount = calculateDiscount(promotion, baseAmount);
        if (discount.compareTo(ZERO) <= 0) {
            if (strict) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            return new PromotionQuote(
                    promotion.getCode(),
                    promotion.getName(),
                    ZERO,
                    "Promotion is not applicable to this booking",
                    promotion.getId());
        }

        return new PromotionQuote(
                promotion.getCode(),
                promotion.getName(),
                discount,
                buildSuccessNote(promotion),
                promotion.getId());
    }

    private boolean matchesScope(Promotion promotion, UUID cinemaId, UUID filmId) {
        Set<UUID> promotionCinemaIds = loadCinemaIds(promotion.getId());
        Set<UUID> promotionFilmIds = loadFilmIds(promotion.getId());

        boolean cinemaScoped = !promotionCinemaIds.isEmpty();
        boolean filmScoped = !promotionFilmIds.isEmpty();

        if (!cinemaScoped && !filmScoped) {
            return true;
        }

        if (cinemaScoped && (cinemaId == null || !promotionCinemaIds.contains(cinemaId))) {
            return false;
        }

        if (filmScoped && (filmId == null || !promotionFilmIds.contains(filmId))) {
            return false;
        }

        return true;
    }

    private boolean hasCinemaOrFilmScope(Promotion promotion) {
        return !loadCinemaIds(promotion.getId()).isEmpty()
                || !loadFilmIds(promotion.getId()).isEmpty();
    }

    private Set<UUID> loadCinemaIds(UUID promotionId) {
        if (promotionId == null) {
            return Set.of();
        }
        List<PromotionCinema> mappings = promotionCinemaRepository.findAllByPromotionId(promotionId);
        if (mappings == null || mappings.isEmpty()) {
            return Set.of();
        }
        return mappings.stream()
                .map(PromotionCinema::getCinemaId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    }

    private Set<UUID> loadFilmIds(UUID promotionId) {
        if (promotionId == null) {
            return Set.of();
        }
        List<PromotionFilm> mappings = promotionFilmRepository.findAllByPromotionId(promotionId);
        if (mappings == null || mappings.isEmpty()) {
            return Set.of();
        }
        return mappings.stream()
                .map(PromotionFilm::getFilmId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    }

    private boolean isPromotionActiveNow(Promotion promotion) {
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

    private BigDecimal calculateDiscount(Promotion promotion, BigDecimal baseAmount) {
        BigDecimal normalizedBaseAmount = normalizeAmount(baseAmount);
        BigDecimal discount = ZERO;
        if (promotion.getDiscountType() == PromotionDiscountType.PERCENT) {
            discount = normalizedBaseAmount
                    .multiply(normalizeAmount(promotion.getDiscountValue()))
                    .divide(HUNDRED, 0, RoundingMode.HALF_UP);
            if (promotion.getMaxDiscountAmount() != null
                    && discount.compareTo(normalizeAmount(promotion.getMaxDiscountAmount())) > 0) {
                discount = normalizeAmount(promotion.getMaxDiscountAmount());
            }
        } else if (promotion.getDiscountType() == PromotionDiscountType.FIXED) {
            discount = normalizeAmount(promotion.getDiscountValue());
        }

        if (discount.compareTo(normalizedBaseAmount) > 0) {
            discount = normalizedBaseAmount;
        }
        return normalizeAmount(discount);
    }

    private String buildSuccessNote(Promotion promotion) {
        return switch (promotion.getDiscountType()) {
            case PERCENT -> "Applied " + normalizeAmount(promotion.getDiscountValue()) + "% discount";
            case FIXED -> "Applied fixed " + normalizeAmount(promotion.getDiscountValue()) + " VND discount";
        };
    }

    private String normalizePromotionCode(String code) {
        if (!StringUtils.hasText(code)) {
            return "";
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return ZERO;
        }
        return amount.setScale(0, RoundingMode.HALF_UP);
    }

    private void ensureRequesterOwnsBooking(UUID requesterUserId, UUID bookingUserId) {
        if (requesterUserId == null || bookingUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!requesterUserId.equals(bookingUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
