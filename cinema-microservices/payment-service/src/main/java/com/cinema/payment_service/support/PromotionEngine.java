package com.cinema.payment_service.support;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.payment_service.dto.request.PromotionPreviewRequest;
import com.cinema.payment_service.dto.response.PromotionSelectionItemResponse;
import com.cinema.payment_service.dto.response.PromotionSelectionResponse;
import com.cinema.payment_service.dto.response.PromotionPreviewResponse;
import com.cinema.payment_service.entity.Promotion;
import com.cinema.payment_service.entity.PromotionCinema;
import com.cinema.payment_service.entity.PromotionFilm;
import com.cinema.payment_service.enums.PromotionDiscountType;
import com.cinema.payment_service.enums.PromotionStatus;
import com.cinema.payment_service.grpc.CustomerRankGrpcClient;
import com.cinema.payment_service.grpc.BookingGrpcClient;
import com.cinema.payment_service.grpc.UserGrpcClient;
import com.cinema.payment_service.mapper.PaymentMapper;
import com.cinema.payment_service.repository.PaymentTransactionPromotionRepository;
import com.cinema.payment_service.repository.PromotionCinemaRepository;
import com.cinema.payment_service.repository.PromotionFilmRepository;
import com.cinema.payment_service.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class PromotionEngine {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(0, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final Duration PROMOTION_SELECTION_CACHE_TTL = Duration.ofSeconds(15);

    private final PromotionRepository promotionRepository;
    private final PromotionCinemaRepository promotionCinemaRepository;
    private final PromotionFilmRepository promotionFilmRepository;
    private final PaymentTransactionPromotionRepository paymentTransactionPromotionRepository;
    private final BookingGrpcClient bookingGrpcClient;
    private final UserGrpcClient userGrpcClient;
    private final CustomerRankGrpcClient customerRankGrpcClient;
    private final PaymentMapper paymentMapper;
    private final ConcurrentHashMap<PromotionSelectionCacheKey, CachedPromotionSelection> selectionCache = new ConcurrentHashMap<>();

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
        UserGrpcClient.UserBasicInfo user = loadUser(requesterUserId);

        PromotionQuote quote = resolvePromotion(
                request.getPromotionId(),
                normalizePromotionCode(request.getPromotionCode()),
                baseAmount,
                bookingContext,
                user,
                requesterUserId,
                false,
                new HashMap<>());

        return paymentMapper.toPromotionPreviewResponse(quote, baseAmount);
    }

    public PromotionSelectionResponse listSelectablePromotions(UUID bookingId, UUID requesterUserId) {
        if (bookingId == null || requesterUserId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        PromotionSelectionCacheKey cacheKey = PromotionSelectionCacheKey.of(bookingId, requesterUserId);
        PromotionSelectionResponse cachedResponse = getCachedSelection(cacheKey);
        if (cachedResponse != null) {
            return cachedResponse;
        }

        BookingGrpcClient.BookingPaymentContext bookingContext = bookingGrpcClient.getBookingPaymentContext(bookingId);
        return listSelectablePromotions(bookingContext, requesterUserId);
    }

    public PromotionSelectionResponse listSelectablePromotions(BookingGrpcClient.BookingPaymentContext bookingContext,
                                                               UUID requesterUserId) {
        if (bookingContext == null || requesterUserId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        PromotionSelectionCacheKey cacheKey = PromotionSelectionCacheKey.of(bookingContext.bookingId(), requesterUserId);
        PromotionSelectionResponse cachedResponse = getCachedSelection(cacheKey);
        if (cachedResponse != null) {
            return cachedResponse;
        }
        ensureRequesterOwnsBooking(requesterUserId, bookingContext.userId());
        BigDecimal baseAmount = normalizeAmount(bookingContext.finalAmount());
        UserGrpcClient.UserBasicInfo user = loadUser(requesterUserId);
        Map<UUID, CustomerRankGrpcClient.CustomerRankInfo> customerRankCache = new HashMap<>();
        List<PromotionSelectionItemResponse> promotions = promotionRepository.findAllByIsDeletedFalse().stream()
                .map(promotion -> {
                    PromotionQuote quote = evaluatePromotion(
                            promotion,
                            baseAmount,
                            bookingContext,
                            user,
                            requesterUserId,
                            customerRankCache);
                    boolean applicable = quote.discountAmount().compareTo(ZERO) > 0;
                    return paymentMapper.toPromotionSelectionItemResponse(quote, applicable, baseAmount);
                })
                .sorted(Comparator
                        .comparing(PromotionSelectionItemResponse::applicable).reversed()
                .thenComparing(PromotionSelectionItemResponse::discountAmount, Comparator.reverseOrder())
                .thenComparing(PromotionSelectionItemResponse::promotionCode, String.CASE_INSENSITIVE_ORDER))
                .toList();

        PromotionSelectionResponse response = paymentMapper.toPromotionSelectionResponse(bookingContext.bookingId(), baseAmount, promotions);
        selectionCache.put(cacheKey, new CachedPromotionSelection(response, Instant.now().plus(PROMOTION_SELECTION_CACHE_TTL)));
        cleanupExpiredSelectionCache();
        return response;
    }

    public PromotionQuote resolvePromotionForCheckout(
            UUID promotionId,
            String promotionCode,
            BigDecimal baseAmount,
            BookingGrpcClient.BookingPaymentContext bookingContext,
            UUID requesterUserId) {
        return resolvePromotion(
                promotionId,
                normalizePromotionCode(promotionCode),
                normalizeAmount(baseAmount),
                bookingContext,
                loadUser(requesterUserId),
                requesterUserId,
                true,
                new HashMap<>());
    }

    public PromotionQuote resolvePromotionForCheckout(
            String promotionCode,
            BigDecimal baseAmount,
            BookingGrpcClient.BookingPaymentContext bookingContext,
            UUID requesterUserId) {
        return resolvePromotionForCheckout(null, promotionCode, baseAmount, bookingContext, requesterUserId);
    }

    private PromotionQuote resolvePromotion(
            UUID promotionId,
            String promotionCode,
            BigDecimal baseAmount,
            BookingGrpcClient.BookingPaymentContext bookingContext,
            UserGrpcClient.UserBasicInfo user,
            UUID requesterUserId,
            boolean strict,
            Map<UUID, CustomerRankGrpcClient.CustomerRankInfo> customerRankCache) {
        if (promotionId == null && !StringUtils.hasText(promotionCode)) {
            return new PromotionQuote("", "", ZERO, "No promotion code", null);
        }

        Promotion promotion = resolvePromotionEntity(promotionId, promotionCode)
                .orElse(null);
        if (promotion == null) {
            if (strict) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            String fallbackCode = StringUtils.hasText(promotionCode) ? promotionCode : "";
            return new PromotionQuote(fallbackCode, "", ZERO, "Promotion code is not supported", null);
        }

        PromotionQuote quote = evaluatePromotion(
                promotion,
                baseAmount,
                bookingContext,
                user,
                requesterUserId,
                customerRankCache);
        if (quote.discountAmount().compareTo(ZERO) <= 0) {
            if (strict) {
                throw resolveStrictPromotionException(promotion, quote);
            }
            return quote;
        }
        return quote;
    }

    private PromotionQuote evaluatePromotion(Promotion promotion,
                                             BigDecimal baseAmount,
                                             BookingGrpcClient.BookingPaymentContext bookingContext,
                                             UserGrpcClient.UserBasicInfo user,
                                             UUID requesterUserId,
                                             Map<UUID, CustomerRankGrpcClient.CustomerRankInfo> customerRankCache) {
        if (promotion == null) {
            return new PromotionQuote("", "", ZERO, "Promotion code is not supported", null);
        }
        if (!isPromotionActiveNow(promotion)) {
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
                return new PromotionQuote(
                        promotion.getCode(),
                        promotion.getName(),
                        ZERO,
                        "Promotion is not applicable to this booking",
                        promotion.getId());
            }
        } else if (hasCinemaOrFilmScope(promotion)) {
            return new PromotionQuote(
                    promotion.getCode(),
                    promotion.getName(),
                    ZERO,
                    "Promotion requires booking context",
                    promotion.getId());
        }

        if (promotion.getMinOrderAmount() != null
                && normalizeAmount(baseAmount).compareTo(normalizeAmount(promotion.getMinOrderAmount())) < 0) {
            return new PromotionQuote(
                    promotion.getCode(),
                    promotion.getName(),
                    ZERO,
                    "Min order is " + normalizeAmount(promotion.getMinOrderAmount()),
                    promotion.getId());
        }
        if (!matchesCustomerRank(promotion, user, customerRankCache)) {
            return new PromotionQuote(
                    promotion.getCode(),
                    promotion.getName(),
                    ZERO,
                    buildRankIneligibleNote(promotion, customerRankCache),
                    promotion.getId());
        }
        if (hasUserReachedPromotionUsageLimit(promotion.getId(), requesterUserId)) {
            return new PromotionQuote(
                    promotion.getCode(),
                    promotion.getName(),
                    ZERO,
                    "You have already used this promotion",
                    promotion.getId());
        }
        if (hasReachedGlobalPromotionUsageLimit(promotion)) {
            return new PromotionQuote(
                    promotion.getCode(),
                    promotion.getName(),
                    ZERO,
                    "Promotion usage limit has been reached",
                    promotion.getId());
        }

        BigDecimal discount = calculateDiscount(promotion, baseAmount);
        if (discount.compareTo(ZERO) <= 0) {
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

    private RuntimeException resolveStrictPromotionException(Promotion promotion, PromotionQuote quote) {
        if (promotion == null) {
            org.slf4j.LoggerFactory.getLogger(PromotionEngine.class).warn(
                    "PROMOTION_CHECK_REJECTED reason=promotion_not_found");
            return new BusinessException(ErrorCode.NOT_FOUND);
        }
        org.slf4j.LoggerFactory.getLogger(PromotionEngine.class).warn(
                "PROMOTION_CHECK_REJECTED promotionId={} promotionCode={} note={} discountAmount={}",
                promotion.getId(),
                promotion.getCode(),
                quote == null ? null : quote.note(),
                quote == null ? null : quote.discountAmount());
        return new BusinessException(ErrorCode.BAD_REQUEST);
    }

    private java.util.Optional<Promotion> resolvePromotionEntity(UUID promotionId, String promotionCode) {
        if (promotionId != null) {
            return promotionRepository.findById(promotionId)
                    .filter(promotion -> !Boolean.TRUE.equals(promotion.getIsDeleted()));
        }
        if (!StringUtils.hasText(promotionCode)) {
            return java.util.Optional.empty();
        }
        return promotionRepository.findByCodeIgnoreCaseAndIsDeletedFalse(promotionCode);
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

    private boolean matchesCustomerRank(Promotion promotion,
                                        UserGrpcClient.UserBasicInfo user,
                                        Map<UUID, CustomerRankGrpcClient.CustomerRankInfo> customerRankCache) {
        if (promotion == null) {
            return true;
        }
        UUID minCustomerRankId = promotion.getMinCustomerRankId();
        if (minCustomerRankId == null) {
            return true;
        }
        CustomerRankGrpcClient.CustomerRankInfo requiredRank =
                resolveCustomerRank(minCustomerRankId, customerRankCache);
        if (requiredRank == null) {
            return false;
        }
        BigDecimal userLifetimePaidAmount = user == null
                ? ZERO
                : normalizeAmount(user.lifetimePaidAmount());
        return userLifetimePaidAmount.compareTo(normalizeAmount(requiredRank.minLifetimeAmount())) >= 0;
    }

    private boolean hasUserReachedPromotionUsageLimit(UUID promotionId, UUID userId) {
        if (promotionId == null || userId == null) {
            return false;
        }
        return paymentTransactionPromotionRepository
                .existsReachedPaidUsageByPromotionIdAndUserId(promotionId, userId);
    }

    private boolean hasReachedGlobalPromotionUsageLimit(Promotion promotion) {
        if (promotion == null || promotion.getId() == null || promotion.getMaxUsageCount() == null) {
            return false;
        }
        return paymentTransactionPromotionRepository.countReachedPaidUsageByPromotionId(promotion.getId())
                >= promotion.getMaxUsageCount();
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

    private String buildRankIneligibleNote(Promotion promotion,
                                          Map<UUID, CustomerRankGrpcClient.CustomerRankInfo> customerRankCache) {
        if (promotion != null && promotion.getMinCustomerRankId() != null) {
            CustomerRankGrpcClient.CustomerRankInfo requiredRank =
                    resolveCustomerRank(promotion.getMinCustomerRankId(), customerRankCache);
            if (requiredRank != null && StringUtils.hasText(requiredRank.name())) {
                return "Promotion requires customer rank " + requiredRank.name() + " or higher";
            }
            if (requiredRank != null && StringUtils.hasText(requiredRank.code())) {
                return "Promotion requires customer rank " + requiredRank.code() + " or higher";
            }
            return "Promotion requires a valid customer rank";
        }
        return "Promotion requires customer rank";
    }

    private CustomerRankGrpcClient.CustomerRankInfo resolveCustomerRank(
            UUID rankId,
            Map<UUID, CustomerRankGrpcClient.CustomerRankInfo> customerRankCache) {
        if (rankId == null) {
            return null;
        }
        if (customerRankCache != null && customerRankCache.containsKey(rankId)) {
            return customerRankCache.get(rankId);
        }
        try {
            CustomerRankGrpcClient.CustomerRankInfo rankInfo = customerRankGrpcClient.getCustomerRankById(rankId);
            if (!isActiveRank(rankInfo)) {
                if (customerRankCache != null) {
                    customerRankCache.put(rankId, null);
                }
                return null;
            }
            if (customerRankCache != null) {
                customerRankCache.put(rankId, rankInfo);
            }
            return rankInfo;
        } catch (BusinessException ex) {
            if (customerRankCache != null) {
                customerRankCache.put(rankId, null);
            }
            return null;
        }
    }

    private boolean isActiveRank(CustomerRankGrpcClient.CustomerRankInfo rankInfo) {
        return rankInfo != null
                && rankInfo.status() != null
                && "ACTIVE".equalsIgnoreCase(rankInfo.status());
    }

    private UserGrpcClient.UserBasicInfo loadUser(UUID requesterUserId) {
        if (requesterUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userGrpcClient.getUserBasicById(requesterUserId);
    }

    private String normalizePromotionCode(String code) {
        if (!StringUtils.hasText(code)) {
            return "";
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private PromotionSelectionResponse getCachedSelection(PromotionSelectionCacheKey cacheKey) {
        CachedPromotionSelection cached = selectionCache.get(cacheKey);
        if (cached == null) {
            return null;
        }
        if (cached.expiresAt().isBefore(Instant.now())) {
            selectionCache.remove(cacheKey);
            return null;
        }
        return cached.response();
    }

    private void cleanupExpiredSelectionCache() {
        Instant now = Instant.now();
        selectionCache.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expiresAt().isBefore(now));
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

    private record PromotionSelectionCacheKey(
            UUID bookingId,
            UUID requesterUserId) {
        private static PromotionSelectionCacheKey of(UUID bookingId, UUID requesterUserId) {
            return new PromotionSelectionCacheKey(bookingId, requesterUserId);
        }
    }

    private record CachedPromotionSelection(
            PromotionSelectionResponse response,
            Instant expiresAt) {
    }
}
