package com.cinema.showtime_service.services.impl;

import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.request.SortField;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
import com.cinema.showtime_service.dto.request.PricingPolicyCreateRequest;
import com.cinema.showtime_service.dto.request.PricingPolicyField;
import com.cinema.showtime_service.dto.request.PricingPolicyUpdateRequest;
import com.cinema.showtime_service.dto.response.PricingPolicyResponse;
import com.cinema.showtime_service.entity.PricingPolicy;
import com.cinema.showtime_service.grpc.CinemaGrpcClient;
import com.cinema.showtime_service.mapper.PricingPolicyMapper;
import com.cinema.showtime_service.repository.PricingPolicyRepository;
import com.cinema.showtime_service.repository.ShowTimeRepository;
import com.cinema.showtime_service.services.PricingPolicyService;
import com.cinema.text.SearchTextUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PricingPolicyServiceImpl implements PricingPolicyService {

    PricingPolicyRepository pricingPolicyRepository;
    PricingPolicyMapper pricingPolicyMapper;
    ShowTimeRepository showTimeRepository;
    CinemaGrpcClient cinemaGrpcClient;

    @Override
    @Transactional
    public ActionMessageResponse createPricingPolicy(PricingPolicyCreateRequest request,
                                                     HttpServletRequest httpRequest) {
        validateAdminOrManagerRole(httpRequest);
        boolean admin = isAdmin(httpRequest);
        Set<UUID> accessibleCinemaIds = admin ? Set.of() : resolveAccessibleCinemaIdsByUser(httpRequest);
        validateCinemaAccess(request.getCinemaId(), accessibleCinemaIds, admin);
        validatePricingOrder(request.getStandardPrice(), request.getVipPrice(), request.getCouplePrice());

        PricingPolicy pricingPolicy = pricingPolicyMapper.toEntity(request);
        pricingPolicy.setCinemaId(request.getCinemaId());
        pricingPolicyRepository.save(pricingPolicy);
        return ActionMessageResponse.builder()
                .message("Táº¡o chÃ­nh sÃ¡ch giÃ¡ thÃ nh cÃ´ng")
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse updatePricingPolicy(UUID id, PricingPolicyUpdateRequest request,
                                                     HttpServletRequest httpRequest) {
        validateAdminOrManagerRole(httpRequest);
        boolean admin = isAdmin(httpRequest);
        Set<UUID> accessibleCinemaIds = admin ? Set.of() : resolveAccessibleCinemaIdsByUser(httpRequest);
        validateCinemaAccess(request.getCinemaId(), accessibleCinemaIds, admin);

        PricingPolicy pricingPolicy = getActivePricingPolicy(id);
        validatePolicyOwnership(pricingPolicy, request.getCinemaId(), accessibleCinemaIds, admin);
        validatePricingPolicyNotUsed(id);
        validatePricingOrder(request.getStandardPrice(), request.getVipPrice(), request.getCouplePrice());

        pricingPolicy.setName(request.getName());
        pricingPolicy.setStandardPrice(request.getStandardPrice());
        pricingPolicy.setVipPrice(request.getVipPrice());
        pricingPolicy.setCouplePrice(request.getCouplePrice());
        pricingPolicyRepository.save(pricingPolicy);
        return ActionMessageResponse.builder()
                .message("Cáº­p nháº­t chÃ­nh sÃ¡ch giÃ¡ thÃ nh cÃ´ng")
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse deletePricingPolicy(UUID id, HttpServletRequest httpRequest) {
        validateAdminOrManagerRole(httpRequest);
        boolean admin = isAdmin(httpRequest);
        Set<UUID> accessibleCinemaIds = admin ? Set.of() : resolveAccessibleCinemaIdsByUser(httpRequest);

        PricingPolicy pricingPolicy = getActivePricingPolicy(id);
        validatePolicyAccess(pricingPolicy, accessibleCinemaIds, admin);
        validatePricingPolicyNotUsed(id);
        pricingPolicy.setIsDeleted(true);
        pricingPolicyRepository.save(pricingPolicy);
        return ActionMessageResponse.builder()
                .message("XÃ³a chÃ­nh sÃ¡ch giÃ¡ thÃ nh cÃ´ng")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PricingPolicyResponse getPricingPolicyById(UUID id, HttpServletRequest httpRequest) {
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        PricingPolicy pricingPolicy = getActivePricingPolicy(id);

        if (HeaderNames.ROLE_ADMIN.equals(role)) {
            return pricingPolicyMapper.toResponse(pricingPolicy);
        }

        RequestAuthUtils.requireAnyRole(
                httpRequest,
                log,
                "pricing_policy_read_action",
                HeaderNames.ROLE_MANAGER,
                HeaderNames.ROLE_STAFF);
        Set<UUID> accessibleCinemaIds = resolveAccessibleCinemaIdsByUser(httpRequest);
        validatePolicyAccess(pricingPolicy, accessibleCinemaIds, false);
        return pricingPolicyMapper.toResponse(pricingPolicy);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PricingPolicyResponse> searchPricingPolicies(PageRequest<PricingPolicyField> request,
                                                                     HttpServletRequest httpRequest) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        List<PricingPolicy> policies = loadVisiblePricingPolicies(role, httpRequest);
        if (policies.isEmpty()) {
            return emptyPageResponse(request);
        }

        List<PricingPolicy> filtered = applySearchRequest(policies, request);
        int page = request.getPageOrDefault();
        int size = request.getSizeOrDefault();
        long totalElements = filtered.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        List<PricingPolicy> pageItems = paginate(filtered, page, size);
        List<PricingPolicyResponse> data = pageItems.stream()
                .map(pricingPolicyMapper::toResponse)
                .toList();

        return PageResponse.<PricingPolicyResponse>builder()
                .data(data)
                .currentPage(page)
                .totalPages(totalPages)
                .totalElements(totalElements)
                .size(size)
                .hasNext(page < totalPages)
                .hasPrevious(page > 1)
                .build();
    }

    private PricingPolicy getActivePricingPolicy(UUID id) {
        return pricingPolicyRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void validatePolicyAccess(
            PricingPolicy pricingPolicy,
            Set<UUID> accessibleCinemaIds,
            boolean bypassCinemaAccessCheck) {
        if (!bypassCinemaAccessCheck && !accessibleCinemaIds.contains(pricingPolicy.getCinemaId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validatePolicyOwnership(
            PricingPolicy pricingPolicy,
            UUID requestCinemaId,
            Set<UUID> accessibleCinemaIds,
            boolean bypassCinemaAccessCheck) {
        if (!requestCinemaId.equals(pricingPolicy.getCinemaId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        validatePolicyAccess(pricingPolicy, accessibleCinemaIds, bypassCinemaAccessCheck);
    }

    private void validateAdminOrManagerRole(HttpServletRequest httpRequest) {
        RequestAuthUtils.requireAnyRole(
                httpRequest,
                log,
                "pricing_policy_write_action",
                HeaderNames.ROLE_ADMIN,
                HeaderNames.ROLE_MANAGER);
    }

    private boolean isAdmin(HttpServletRequest httpRequest) {
        return HeaderNames.ROLE_ADMIN.equals(RequestAuthUtils.requireRoleHeader(httpRequest));
    }

    private void validatePricingPolicyNotUsed(UUID pricingPolicyId) {
        if (showTimeRepository.existsByPricingPolicyIdAndIsDeletedFalse(pricingPolicyId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private void validatePricingOrder(Long standardPrice, Long vipPrice, Long couplePrice) {
        if (!(standardPrice < vipPrice && vipPrice < couplePrice)) {
            throw new BusinessException(ErrorCode.INVALID_PRICING_ORDER);
        }
    }

    private List<PricingPolicy> loadVisiblePricingPolicies(String role, HttpServletRequest httpRequest) {
        if (HeaderNames.ROLE_ADMIN.equals(role)) {
            return pricingPolicyRepository.findAllByIsDeletedFalseOrderByTimeCreatedDesc();
        }

        RequestAuthUtils.requireAnyRole(
                httpRequest,
                log,
                "pricing_policy_read_action",
                HeaderNames.ROLE_MANAGER,
                HeaderNames.ROLE_STAFF);

        Set<UUID> accessibleCinemaIds = resolveAccessibleCinemaIdsByUser(httpRequest);
        if (accessibleCinemaIds.isEmpty()) {
            return List.of();
        }

        return pricingPolicyRepository.findAllByCinemaIdInAndIsDeletedFalseOrderByTimeCreatedDesc(accessibleCinemaIds);
    }

    private PageResponse<PricingPolicyResponse> emptyPageResponse(PageRequest<PricingPolicyField> request) {
        int page = request.getPageOrDefault();
        int size = request.getSizeOrDefault();
        return PageResponse.<PricingPolicyResponse>builder()
                .data(List.of())
                .currentPage(page)
                .totalPages(0)
                .totalElements(0)
                .size(size)
                .hasNext(false)
                .hasPrevious(page > 1)
                .build();
    }

    private List<PricingPolicy> applySearchRequest(List<PricingPolicy> items, PageRequest<PricingPolicyField> request) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<PricingPolicy> filtered = new ArrayList<>(items);
        String keyword = request.getNormalizedKeyword();
        if (StringUtils.hasText(keyword)) {
            filtered = filtered.stream()
                    .filter(item -> matchesKeyword(item, keyword))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        List<FilterField<PricingPolicyField>> filters = request.getFilterBy();
        if (filters != null && !filters.isEmpty()) {
            filtered = filtered.stream()
                    .filter(item -> matchesAllFilters(item, filters))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        filtered.sort(buildComparator(request.getSortBy()));
        return filtered;
    }

    private Comparator<PricingPolicy> buildComparator(List<SortField<PricingPolicyField>> sortFields) {
        Comparator<PricingPolicy> defaultComparator = Comparator
                .comparing(PricingPolicy::getTimeCreated, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PricingPolicy::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

        if (sortFields == null || sortFields.isEmpty()) {
            return defaultComparator;
        }

        Comparator<PricingPolicy> merged = null;
        for (SortField<PricingPolicyField> sort : sortFields) {
            if (sort == null || sort.getField() == null) {
                continue;
            }

            Comparator<PricingPolicy> fieldComparator = (left, right) -> compareValues(
                    sort.getField().getFieldValue(left),
                    sort.getField().getFieldValue(right));
            if ("DESC".equalsIgnoreCase(sort.getDirection())) {
                fieldComparator = fieldComparator.reversed();
            }
            merged = merged == null ? fieldComparator : merged.thenComparing(fieldComparator);
        }

        return merged == null ? defaultComparator : merged;
    }

    private boolean matchesKeyword(PricingPolicy item, String keyword) {
        if (!StringUtils.hasText(keyword) || item == null) {
            return true;
        }
        return SearchTextUtils.containsIgnoreCase(item.getId() == null ? null : item.getId().toString(), keyword)
                || SearchTextUtils.containsIgnoreCase(item.getName(), keyword)
                || SearchTextUtils.containsIgnoreCase(item.getCinemaId() == null ? null : item.getCinemaId().toString(), keyword)
                || SearchTextUtils.containsIgnoreCase(item.getTimeCreated() == null ? null : item.getTimeCreated().toString(), keyword)
                || SearchTextUtils.containsIgnoreCase(item.getTimeUpdated() == null ? null : item.getTimeUpdated().toString(), keyword);
    }

    private boolean matchesAllFilters(PricingPolicy item, List<FilterField<PricingPolicyField>> filters) {
        for (FilterField<PricingPolicyField> filter : filters) {
            if (!matchesFilter(item, filter)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesFilter(PricingPolicy item, FilterField<PricingPolicyField> filter) {
        if (item == null || filter == null || filter.getField() == null || filter.getOperator() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        String operator = filter.getOperator().trim().toUpperCase(Locale.ROOT);
        Object rawValue = filter.getValue();
        if (rawValue == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        Object fieldValue = filter.getField().getFieldValue(item);
        Class<?> dataType = filter.getField().getDataType();

        return switch (operator) {
            case "EQ" -> compareValues(fieldValue, PricingPolicyField.convertValue(String.valueOf(rawValue), dataType)) == 0;
            case "NEQ" -> compareValues(fieldValue, PricingPolicyField.convertValue(String.valueOf(rawValue), dataType)) != 0;
            case "LIKE" -> fieldValue instanceof String text
                    && SearchTextUtils.containsIgnoreCase(text, String.valueOf(rawValue));
            case "GTE" -> compareValues(fieldValue, PricingPolicyField.convertValue(String.valueOf(rawValue), dataType)) >= 0;
            case "LTE" -> compareValues(fieldValue, PricingPolicyField.convertValue(String.valueOf(rawValue), dataType)) <= 0;
            case "IN" -> matchesInValues(fieldValue, rawValue, dataType);
            case "BETWEEN" -> matchesBetweenValues(fieldValue, rawValue, dataType);
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
                values.add(PricingPolicyField.convertValue(String.valueOf(item), dataType));
            }
            return values;
        }

        if (rawValue.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(rawValue);
            for (int i = 0; i < length; i++) {
                values.add(PricingPolicyField.convertValue(String.valueOf(java.lang.reflect.Array.get(rawValue, i)), dataType));
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
            values.add(PricingPolicyField.convertValue(value, dataType));
        }
        return values;
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

    private List<PricingPolicy> paginate(List<PricingPolicy> items, int page, int size) {
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

    private void validateCinemaAccess(UUID cinemaId, Set<UUID> accessibleCinemaIds, boolean bypassCinemaAccessCheck) {
        if (cinemaId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!bypassCinemaAccessCheck && !accessibleCinemaIds.contains(cinemaId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private Set<UUID> resolveAccessibleCinemaIdsByUser(HttpServletRequest httpRequest) {
        try {
            UUID userId = RequestAuthUtils.requireUserId(httpRequest);
            String role = RequestAuthUtils.requireRoleHeader(httpRequest);
            List<UUID> cinemaIds = cinemaGrpcClient.getCinemaIdsByUserId(userId, role);
            return new HashSet<>(cinemaIds == null ? List.of() : cinemaIds);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.UNAUTHORIZED || ex.getErrorCode() == ErrorCode.INVALID_FORMAT) {
                log.warn("Invalid auth headers method={} path={}", httpRequest.getMethod(), httpRequest.getRequestURI());
            }
            throw ex;
        }
    }
}
