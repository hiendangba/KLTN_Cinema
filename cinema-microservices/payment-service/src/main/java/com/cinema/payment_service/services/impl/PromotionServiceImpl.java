package com.cinema.payment_service.services.impl;

import com.cinema.Enum.SuccessMessage;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.request.SortField;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
import com.cinema.payment_service.dto.request.PromotionField;
import com.cinema.payment_service.dto.request.PromotionUpsertRequest;
import com.cinema.payment_service.dto.response.CustomerRankSummaryResponse;
import com.cinema.payment_service.dto.response.PromotionResponse;
import com.cinema.payment_service.entity.Promotion;
import com.cinema.payment_service.entity.PromotionCinema;
import com.cinema.payment_service.entity.PromotionFilm;
import com.cinema.payment_service.enums.PromotionDiscountType;
import com.cinema.payment_service.enums.PromotionStatus;
import com.cinema.payment_service.grpc.CinemaGrpcClient;
import com.cinema.payment_service.grpc.CustomerRankGrpcClient;
import com.cinema.payment_service.mapper.PaymentMapper;
import com.cinema.payment_service.repository.PromotionCinemaRepository;
import com.cinema.payment_service.repository.PromotionFilmRepository;
import com.cinema.payment_service.repository.PaymentTransactionPromotionRepository;
import com.cinema.payment_service.repository.PromotionRepository;
import com.cinema.payment_service.services.PromotionService;
import com.cinema.payment_service.support.PromotionEngine;
import com.cinema.text.SearchTextUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionCinemaRepository promotionCinemaRepository;
    private final PromotionFilmRepository promotionFilmRepository;
    private final PaymentTransactionPromotionRepository paymentTransactionPromotionRepository;
    private final CinemaGrpcClient cinemaGrpcClient;
    private final CustomerRankGrpcClient customerRankGrpcClient;
    private final PaymentMapper paymentMapper;
    private final PromotionEngine promotionEngine;

    @Override
    @Transactional
    public ActionMessageResponse createPromotion(PromotionUpsertRequest request, HttpServletRequest httpRequest) {
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        UUID requesterUserId = RequestAuthUtils.requireUserId(httpRequest);
        validateUpsertRequest(request);

        String code = normalizePromotionCode(request.getCode());
        ensurePromotionCodeAvailable(code, null);

        Promotion promotion = new Promotion();
        applyUpsertRequest(promotion, request);
        promotion.setCode(code);
        promotion.setCreatedByUserId(requesterUserId);
        promotion.setCreatedByRole(role);
        promotion.setStatus(PromotionStatus.ACTIVE);

        List<UUID> cinemaIds = normalizeUuidList(request.getCinemaIds());
        List<UUID> filmIds = normalizeUuidList(request.getFilmIds());
        validateScope(role, requesterUserId, cinemaIds, filmIds);

        promotion = promotionRepository.save(promotion);
        replaceMappings(promotion.getId(), cinemaIds, filmIds);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PROMOTION_CREATED.getMessage())
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse updatePromotion(UUID id, PromotionUpsertRequest request, HttpServletRequest httpRequest) {
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        UUID requesterUserId = RequestAuthUtils.requireUserId(httpRequest);
        validateUpsertRequest(request);

        Promotion promotion = getPromotionEntity(id);
        ensureCanManagePromotion(role, requesterUserId, promotion);

        String code = normalizePromotionCode(request.getCode());
        ensurePromotionCodeAvailable(code, id);

        applyUpsertRequest(promotion, request);
        promotion.setCode(code);
        if (request.getStatus() != null) {
            promotion.setStatus(request.getStatus());
        }

        List<UUID> cinemaIds = normalizeUuidList(request.getCinemaIds());
        List<UUID> filmIds = normalizeUuidList(request.getFilmIds());
        validateScope(role, requesterUserId, cinemaIds, filmIds);

        promotion = promotionRepository.save(promotion);
        replaceMappings(promotion.getId(), cinemaIds, filmIds);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PROMOTION_UPDATED.getMessage())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionResponse getPromotionById(UUID id, HttpServletRequest httpRequest) {
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        UUID requesterUserId = RequestAuthUtils.requireUserId(httpRequest);
        Promotion promotion = getPromotionEntity(id);
        ensureCanViewPromotion(role, requesterUserId, promotion);
        long usedCount = getUsedCount(promotion.getId());
        CustomerRankSummaryResponse customerRank = resolveCustomerRankSummary(promotion.getMinCustomerRankId(), new LinkedHashMap<>());
        return paymentMapper.toPromotionResponse(
                promotion,
                loadCinemaIds(promotion.getId()),
                loadFilmIds(promotion.getId()),
                customerRank,
                usedCount,
                calculateRemainingUsageCount(promotion, usedCount));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PromotionResponse> searchPromotions(PageRequest<PromotionField> request,
            HttpServletRequest httpRequest) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        UUID requesterUserId = RequestAuthUtils.requireUserId(httpRequest);
        List<Promotion> promotions = promotionRepository.findAllByIsDeletedFalse();
        promotions = applyVisibilityFilter(promotions, role, requesterUserId);
        promotions = applyPromotionPageRequest(promotions, request, role, requesterUserId);

        int page = request.getPageOrDefault();
        int size = request.getSizeOrDefault();
        long totalElements = promotions.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        List<Promotion> pageItems = paginate(promotions, page, size);
        List<PromotionResponse> data = buildResponses(pageItems);

        return PageResponse.<PromotionResponse>builder()
                .data(data)
                .currentPage(page)
                .totalPages(totalPages)
                .totalElements(totalElements)
                .size(size)
                .hasNext(page < totalPages)
                .hasPrevious(page > 1)
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse deletePromotion(UUID id, HttpServletRequest httpRequest) {
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        UUID requesterUserId = RequestAuthUtils.requireUserId(httpRequest);
        Promotion promotion = getPromotionEntity(id);
        ensureCanManagePromotion(role, requesterUserId, promotion);

        promotion.setStatus(PromotionStatus.INACTIVE);
        promotion.setIsDeleted(true);
        promotionRepository.save(promotion);
        promotionCinemaRepository.deleteByPromotionId(id);
        promotionFilmRepository.deleteByPromotionId(id);

        return ActionMessageResponse.builder()
                .message(SuccessMessage.PROMOTION_DELETED.getMessage())
                .build();
    }

    private void validateUpsertRequest(PromotionUpsertRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (request.getDiscountType() == PromotionDiscountType.PERCENT) {
            if (request.getDiscountValue() == null
                    || request.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0
                    || request.getDiscountValue().compareTo(new BigDecimal("100")) > 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
        } else if (request.getDiscountType() == PromotionDiscountType.FIXED) {
            if (request.getDiscountValue() == null || request.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
        }

        if (request.getStartAt() != null && request.getEndAt() != null
                && request.getEndAt().isBefore(request.getStartAt())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (request.getMinOrderAmount() != null && request.getMinOrderAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (request.getMaxDiscountAmount() != null
                && request.getMaxDiscountAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (request.getMaxUsageCount() != null && request.getMaxUsageCount() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private void validateScope(String role, UUID requesterUserId, List<UUID> cinemaIds, List<UUID> filmIds) {
        if (HeaderNames.ROLE_ADMIN.equals(role)) {
            return;
        }

        if (!HeaderNames.ROLE_MANAGER.equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<UUID> accessibleCinemaIds = loadAccessibleCinemaIds(requesterUserId, role);
        if (accessibleCinemaIds.isEmpty()) {
            throw new BusinessException(ErrorCode.MANAGER_NOT_ASSIGNED_CINEMA);
        }
        if (cinemaIds == null || cinemaIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (!accessibleCinemaIds.containsAll(cinemaIds)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void ensureCanManagePromotion(String role, UUID requesterUserId, Promotion promotion) {
        if (HeaderNames.ROLE_ADMIN.equals(role)) {
            return;
        }
        if (!HeaderNames.ROLE_MANAGER.equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (promotion == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        List<UUID> accessibleCinemaIds = loadAccessibleCinemaIds(requesterUserId, role);
        if (accessibleCinemaIds.isEmpty()) {
            throw new BusinessException(ErrorCode.MANAGER_NOT_ASSIGNED_CINEMA);
        }
        List<UUID> promotionCinemaIds = loadCinemaIds(promotion.getId());
        if (promotionCinemaIds.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!accessibleCinemaIds.containsAll(promotionCinemaIds)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void ensureCanViewPromotion(String role, UUID requesterUserId, Promotion promotion) {
        if (HeaderNames.ROLE_ADMIN.equals(role)) {
            return;
        }
        if (!HeaderNames.ROLE_MANAGER.equals(role) && !HeaderNames.ROLE_STAFF.equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<UUID> accessibleCinemaIds = loadAccessibleCinemaIds(requesterUserId, role);
        if (accessibleCinemaIds.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!isVisibleToCinemaScope(promotion, accessibleCinemaIds)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private Promotion getPromotionEntity(UUID id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return promotionRepository.findById(id)
                .filter(promotion -> Boolean.FALSE.equals(promotion.getIsDeleted()) || promotion.getIsDeleted() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void ensurePromotionCodeAvailable(String code, UUID idToIgnore) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        boolean existed = idToIgnore == null
                ? promotionRepository.existsByCodeIgnoreCaseAndIsDeletedFalse(code)
                : promotionRepository.existsByCodeIgnoreCaseAndIsDeletedFalseAndIdNot(code, idToIgnore);
        if (existed) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private void applyUpsertRequest(Promotion promotion, PromotionUpsertRequest request) {
        promotion.setName(request.getName().trim());
        promotion.setDescription(normalizeNullableString(request.getDescription()));
        promotion.setDiscountType(request.getDiscountType());
        promotion.setDiscountValue(normalizeAmount(request.getDiscountValue()));
        promotion.setMinOrderAmount(normalizeNullableAmount(request.getMinOrderAmount()));
        promotion.setMaxDiscountAmount(normalizeNullableAmount(request.getMaxDiscountAmount()));
        promotion.setMinCustomerRankId(request.getMinCustomerRankId());
        promotion.setMaxUsageCount(request.getMaxUsageCount());
        promotion.setStartAt(request.getStartAt());
        promotion.setEndAt(request.getEndAt());
        promotion.setIsDeleted(false);
    }

    private List<Promotion> applyVisibilityFilter(List<Promotion> promotions, String role, UUID requesterUserId) {
        if (promotions == null || promotions.isEmpty() || HeaderNames.ROLE_ADMIN.equals(role)) {
            return promotions == null ? List.of() : promotions;
        }
        if (!HeaderNames.ROLE_MANAGER.equals(role) && !HeaderNames.ROLE_STAFF.equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<UUID> accessibleCinemaIds = loadAccessibleCinemaIds(requesterUserId, role);
        if (accessibleCinemaIds.isEmpty()) {
            return List.of();
        }

        return promotions.stream()
                .filter(promotion -> isVisibleToCinemaScope(promotion, accessibleCinemaIds))
                .toList();
    }

    private boolean isVisibleToCinemaScope(Promotion promotion, List<UUID> accessibleCinemaIds) {
        if (promotion == null) {
            return false;
        }
        List<UUID> promotionCinemaIds = loadCinemaIds(promotion.getId());
        if (promotionCinemaIds.isEmpty()) {
            return true;
        }
        return accessibleCinemaIds.stream().anyMatch(promotionCinemaIds::contains);
    }

    private List<Promotion> applyPromotionPageRequest(
            List<Promotion> items,
            PageRequest<PromotionField> request,
            String role,
            UUID requesterUserId) {
        List<FilterField<PromotionField>> filters = request.getFilterBy();
        if (filters != null && !filters.isEmpty()) {
            validatePromotionSearchFilters(filters, role, requesterUserId);
        }

        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<Promotion> filtered = new ArrayList<>(items);
        String keyword = request.getNormalizedKeyword();
        if (StringUtils.hasText(keyword)) {
            filtered = filtered.stream()
                    .filter(item -> matchesKeyword(item, keyword))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        if (filters != null && !filters.isEmpty()) {
            filtered = filtered.stream()
                    .filter(item -> matchesAllFilters(item, filters))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        filtered.sort(buildPromotionComparator(request.getSortBy()));
        return filtered;
    }

    private Comparator<Promotion> buildPromotionComparator(List<SortField<PromotionField>> sortFields) {
        Comparator<Promotion> comparator = Comparator
                .comparing(Promotion::getTimeCreated, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(Promotion::getCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

        if (sortFields == null || sortFields.isEmpty()) {
            return comparator;
        }

        Comparator<Promotion> merged = null;
        for (SortField<PromotionField> sort : sortFields) {
            if (sort == null || sort.getField() == null) {
                continue;
            }
            Comparator<Promotion> fieldComparator = (left, right) -> compareValues(
                    sort.getField().getFieldValue(left),
                    sort.getField().getFieldValue(right));
            if ("DESC".equalsIgnoreCase(sort.getDirection())) {
                fieldComparator = fieldComparator.reversed();
            }
            merged = merged == null ? fieldComparator : merged.thenComparing(fieldComparator);
        }

        return merged == null ? comparator : merged;
    }

    private boolean matchesKeyword(Promotion item, String keyword) {
        if (!StringUtils.hasText(keyword) || item == null) {
            return true;
        }
        return SearchTextUtils.containsIgnoreCase(item.getCode(), keyword)
                || SearchTextUtils.containsIgnoreCase(item.getName(), keyword)
                || SearchTextUtils.containsIgnoreCase(item.getDescription(), keyword)
                || SearchTextUtils.containsIgnoreCase(item.getCreatedByRole(), keyword)
                || SearchTextUtils.containsIgnoreCase(item.getId() == null ? null : item.getId().toString(), keyword);
    }

    private boolean matchesAllFilters(Promotion item, List<FilterField<PromotionField>> filters) {
        for (FilterField<PromotionField> filter : filters) {
            if (!matchesFilter(item, filter)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesFilter(Promotion item, FilterField<PromotionField> filter) {
        if (item == null || filter == null || filter.getField() == null || filter.getOperator() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (filter.getField() == PromotionField.CINEMA_ID) {
            return matchesCinemaFilter(item, filter);
        }
        if (filter.getField() == PromotionField.FILM_ID) {
            return matchesFilmFilter(item, filter);
        }

        String operator = filter.getOperator().trim().toUpperCase(Locale.ROOT);
        Object rawValue = filter.getValue();
        if (rawValue == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        Object fieldValue = filter.getField().getFieldValue(item);
        Class<?> dataType = filter.getField().getDataType();

        return switch (operator) {
            case "EQ" -> compareValues(fieldValue, PromotionField.convertValue(String.valueOf(rawValue), dataType)) == 0;
            case "NEQ" -> compareValues(fieldValue, PromotionField.convertValue(String.valueOf(rawValue), dataType)) != 0;
            case "LIKE" -> fieldValue instanceof String text
                    && SearchTextUtils.containsIgnoreCase(text, String.valueOf(rawValue));
            case "GTE" -> compareValues(fieldValue, PromotionField.convertValue(String.valueOf(rawValue), dataType)) >= 0;
            case "LTE" -> compareValues(fieldValue, PromotionField.convertValue(String.valueOf(rawValue), dataType)) <= 0;
            case "IN" -> matchesInValues(fieldValue, rawValue, dataType);
            case "BETWEEN" -> matchesBetweenValues(fieldValue, rawValue, dataType);
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST);
        };
    }

    private void validatePromotionSearchFilters(
            List<FilterField<PromotionField>> filters,
            String role,
            UUID requesterUserId) {
        if (filters == null || filters.isEmpty()) {
            return;
        }

        for (FilterField<PromotionField> filter : filters) {
            if (filter == null) {
                continue;
            }

            if (filter.getField() == PromotionField.FILM_ID) {
                continue;
            }

            if (filter.getField() == PromotionField.CINEMA_ID) {
                List<UUID> requestedCinemaIds = convertToUuidList(filter.getValue());
                if (requestedCinemaIds.isEmpty()) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST);
                }

                String operator = filter.getOperator() == null ? "" : filter.getOperator().trim().toUpperCase(Locale.ROOT);
                if (!"IN".equals(operator) && !"EQ".equals(operator)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST);
                }

                if (HeaderNames.ROLE_ADMIN.equals(role)) {
                    continue;
                }
                if (!HeaderNames.ROLE_MANAGER.equals(role) && !HeaderNames.ROLE_STAFF.equals(role)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                }

                List<UUID> accessibleCinemaIds = loadAccessibleCinemaIds(requesterUserId, role);
                if (accessibleCinemaIds.isEmpty()) {
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                }
                if (!accessibleCinemaIds.containsAll(requestedCinemaIds)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                }
            }
        }
    }

    private boolean matchesCinemaFilter(Promotion item, FilterField<PromotionField> filter) {
        if (item == null || filter == null || filter.getValue() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        String operator = filter.getOperator().trim().toUpperCase(Locale.ROOT);
        List<UUID> promotionCinemaIds = loadCinemaIds(item.getId());
        if (promotionCinemaIds.isEmpty()) {
            return false;
        }

        return switch (operator) {
            case "EQ", "IN" -> {
                List<UUID> requestedCinemaIds = convertToUuidList(filter.getValue());
                yield requestedCinemaIds.stream().anyMatch(promotionCinemaIds::contains);
            }
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST);
        };
    }

    private boolean matchesFilmFilter(Promotion item, FilterField<PromotionField> filter) {
        if (item == null || filter == null || filter.getValue() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        String operator = filter.getOperator().trim().toUpperCase(Locale.ROOT);
        List<UUID> promotionFilmIds = loadFilmIds(item.getId());
        if (promotionFilmIds.isEmpty()) {
            return false;
        }

        return switch (operator) {
            case "EQ", "IN" -> {
                List<UUID> requestedFilmIds = convertToUuidList(filter.getValue());
                yield requestedFilmIds.stream().anyMatch(promotionFilmIds::contains);
            }
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST);
        };
    }

    private boolean matchesInValues(Object fieldValue, Object rawValue, Class<?> dataType) {
        List<Comparable<?>> values = convertToComparableList(rawValue, dataType);
        for (Comparable<?> value : values) {
            if (compareValues(fieldValue, value) == 0) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesBetweenValues(Object fieldValue, Object rawValue, Class<?> dataType) {
        List<Comparable<?>> values = convertToComparableList(rawValue, dataType);
        if (values.size() != 2) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        Comparable<?> start = values.get(0);
        Comparable<?> end = values.get(1);
        if (compareValues(start, end) > 0) {
            Comparable<?> tmp = start;
            start = end;
            end = tmp;
        }
        return compareValues(fieldValue, start) >= 0 && compareValues(fieldValue, end) <= 0;
    }

    private List<Comparable<?>> convertToComparableList(Object rawValue, Class<?> dataType) {
        if (rawValue == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        List<Comparable<?>> values = new ArrayList<>();
        if (rawValue instanceof Collection<?> collection) {
            for (Object item : collection) {
                values.add(PromotionField.convertValue(String.valueOf(item), dataType));
            }
            return values;
        }

        if (rawValue.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(rawValue);
            for (int i = 0; i < length; i++) {
                values.add(PromotionField.convertValue(String.valueOf(java.lang.reflect.Array.get(rawValue, i)), dataType));
            }
            return values;
        }

        String text = String.valueOf(rawValue).trim();
        if (text.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        for (String token : text.split(",")) {
            String value = token.trim();
            if (value.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            values.add(PromotionField.convertValue(value, dataType));
        }
        return values;
    }

    private List<UUID> convertToUuidList(Object rawValue) {
        List<Comparable<?>> comparableValues = convertToComparableList(rawValue, UUID.class);
        return comparableValues.stream()
                .map(value -> (UUID) value)
                .distinct()
                .toList();
    }

    private int compareValues(Object left, Object right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        if (left instanceof Comparable<?> comparableLeft) {
            try {
                @SuppressWarnings("unchecked")
                Comparable<Object> typedLeft = (Comparable<Object>) comparableLeft;
                return typedLeft.compareTo(right);
            } catch (ClassCastException ex) {
                return String.valueOf(left).compareToIgnoreCase(String.valueOf(right));
            }
        }
        return String.valueOf(left).compareToIgnoreCase(String.valueOf(right));
    }

    private List<Promotion> paginate(List<Promotion> items, int page, int size) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        int fromIndex = Math.max(0, (page - 1) * size);
        if (fromIndex >= items.size()) {
            return List.of();
        }
        int toIndex = Math.min(items.size(), fromIndex + size);
        return new ArrayList<>(items.subList(fromIndex, toIndex));
    }

    private List<PromotionResponse> buildResponses(List<Promotion> promotions) {
        if (promotions == null || promotions.isEmpty()) {
            return List.of();
        }

        List<UUID> promotionIds = promotions.stream()
                .map(Promotion::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, List<UUID>> cinemaMap = loadCinemaIdsByPromotionIds(promotionIds);
        Map<UUID, List<UUID>> filmMap = loadFilmIdsByPromotionIds(promotionIds);
        Map<UUID, Long> usageMap = loadUsedCountByPromotionIds(promotionIds);
        Map<UUID, CustomerRankSummaryResponse> customerRankMap = new LinkedHashMap<>();

        return promotions.stream()
                .map(promotion -> {
                    long usedCount = usageMap.getOrDefault(promotion.getId(), 0L);
                    CustomerRankSummaryResponse customerRank =
                            resolveCustomerRankSummary(promotion.getMinCustomerRankId(), customerRankMap);
                    return paymentMapper.toPromotionResponse(
                            promotion,
                            cinemaMap.getOrDefault(promotion.getId(), List.of()),
                            filmMap.getOrDefault(promotion.getId(), List.of()),
                            customerRank,
                            usedCount,
                            calculateRemainingUsageCount(promotion, usedCount));
                })
                .toList();
    }

    private Map<UUID, Long> loadUsedCountByPromotionIds(List<UUID> promotionIds) {
        if (promotionIds == null || promotionIds.isEmpty()) {
            return Map.of();
        }
        List<PaymentTransactionPromotionRepository.PromotionUsageSummary> usageSummaries =
                paymentTransactionPromotionRepository.summarizeReachedPaidUsageByPromotionIds(promotionIds);
        if (usageSummaries == null || usageSummaries.isEmpty()) {
            return Map.of();
        }
        return usageSummaries.stream()
                .filter(summary -> summary != null && summary.getPromotionId() != null)
                .collect(Collectors.toMap(
                        PaymentTransactionPromotionRepository.PromotionUsageSummary::getPromotionId,
                        PaymentTransactionPromotionRepository.PromotionUsageSummary::getUsedCount,
                        Long::max,
                        LinkedHashMap::new));
    }

    private long getUsedCount(UUID promotionId) {
        if (promotionId == null) {
            return 0L;
        }
        return paymentTransactionPromotionRepository.countReachedPaidUsageByPromotionId(promotionId);
    }

    private Long calculateRemainingUsageCount(Promotion promotion, long usedCount) {
        if (promotion == null || promotion.getMaxUsageCount() == null) {
            return null;
        }
        return Math.max(0L, (long) promotion.getMaxUsageCount() - usedCount);
    }

    private CustomerRankSummaryResponse resolveCustomerRankSummary(
            UUID rankId,
            Map<UUID, CustomerRankSummaryResponse> customerRankCache) {
        if (rankId == null) {
            return null;
        }
        if (customerRankCache != null && customerRankCache.containsKey(rankId)) {
            return customerRankCache.get(rankId);
        }
        try {
            CustomerRankGrpcClient.CustomerRankInfo rankInfo = customerRankGrpcClient.getCustomerRankById(rankId);
            if (rankInfo == null) {
                if (customerRankCache != null) {
                    customerRankCache.put(rankId, null);
                }
                return null;
            }
            CustomerRankSummaryResponse customerRank = CustomerRankSummaryResponse.builder()
                    .id(rankInfo.id())
                    .code(rankInfo.code())
                    .name(rankInfo.name())
                    .build();
            if (customerRankCache != null) {
                customerRankCache.put(rankId, customerRank);
            }
            return customerRank;
        } catch (BusinessException ex) {
            if (customerRankCache != null) {
                customerRankCache.put(rankId, null);
            }
            return null;
        }
    }

    private List<UUID> loadCinemaIds(UUID promotionId) {
        if (promotionId == null) {
            return List.of();
        }
        List<PromotionCinema> mappings = promotionCinemaRepository.findAllByPromotionId(promotionId);
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }
        return mappings.stream()
                .map(PromotionCinema::getCinemaId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<UUID> loadFilmIds(UUID promotionId) {
        if (promotionId == null) {
            return List.of();
        }
        List<PromotionFilm> mappings = promotionFilmRepository.findAllByPromotionId(promotionId);
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }
        return mappings.stream()
                .map(PromotionFilm::getFilmId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private Map<UUID, List<UUID>> loadCinemaIdsByPromotionIds(List<UUID> promotionIds) {
        if (promotionIds == null || promotionIds.isEmpty()) {
            return Map.of();
        }
        List<PromotionCinema> mappings = promotionCinemaRepository.findAllByPromotionIdIn(promotionIds);
        if (mappings == null || mappings.isEmpty()) {
            return Map.of();
        }
        return mappings.stream()
                .collect(Collectors.groupingBy(
                        PromotionCinema::getPromotionId,
                        LinkedHashMap::new,
                        Collectors.mapping(PromotionCinema::getCinemaId, Collectors.toList())));
    }

    private Map<UUID, List<UUID>> loadFilmIdsByPromotionIds(List<UUID> promotionIds) {
        if (promotionIds == null || promotionIds.isEmpty()) {
            return Map.of();
        }
        List<PromotionFilm> mappings = promotionFilmRepository.findAllByPromotionIdIn(promotionIds);
        if (mappings == null || mappings.isEmpty()) {
            return Map.of();
        }
        return mappings.stream()
                .collect(Collectors.groupingBy(
                        PromotionFilm::getPromotionId,
                        LinkedHashMap::new,
                        Collectors.mapping(PromotionFilm::getFilmId, Collectors.toList())));
    }

    private void replaceMappings(UUID promotionId, List<UUID> cinemaIds, List<UUID> filmIds) {
        promotionCinemaRepository.deleteByPromotionId(promotionId);
        promotionFilmRepository.deleteByPromotionId(promotionId);

        if (cinemaIds != null && !cinemaIds.isEmpty()) {
            List<PromotionCinema> mappings = new ArrayList<>(cinemaIds.size());
            for (UUID cinemaId : cinemaIds) {
                PromotionCinema mapping = new PromotionCinema();
                mapping.setPromotionId(promotionId);
                mapping.setCinemaId(cinemaId);
                mappings.add(mapping);
            }
            promotionCinemaRepository.saveAll(mappings);
        }

        if (filmIds != null && !filmIds.isEmpty()) {
            List<PromotionFilm> mappings = new ArrayList<>(filmIds.size());
            for (UUID filmId : filmIds) {
                PromotionFilm mapping = new PromotionFilm();
                mapping.setPromotionId(promotionId);
                mapping.setFilmId(filmId);
                mappings.add(mapping);
            }
            promotionFilmRepository.saveAll(mappings);
        }
    }

    private List<UUID> normalizeUuidList(List<UUID> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<UUID> loadAccessibleCinemaIds(UUID requesterUserId, String role) {
        if (requesterUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return cinemaGrpcClient.getCinemasByUserId(requesterUserId, role).stream()
                .map(CinemaGrpcClient.CinemaSummary::id)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private String normalizePromotionCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNullableString(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(0, java.math.RoundingMode.HALF_UP);
        }
        return amount.setScale(0, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeNullableAmount(BigDecimal amount) {
        return amount == null ? null : normalizeAmount(amount);
    }
}
