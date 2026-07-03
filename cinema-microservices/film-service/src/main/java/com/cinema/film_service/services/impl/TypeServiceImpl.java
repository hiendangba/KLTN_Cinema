package com.cinema.film_service.services.impl;

import com.cinema.Enum.SuccessMessage;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.request.SortField;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.film_service.dto.request.CreateFilmTypeRequest;
import com.cinema.film_service.dto.request.FilmTypeField;
import com.cinema.film_service.dto.request.UpdateFilmTypeRequest;
import com.cinema.film_service.dto.response.FilmTypeResponse;
import com.cinema.film_service.entity.FilmType;
import com.cinema.film_service.repository.FilmTypeRepository;
import com.cinema.film_service.services.FilmCatalogSyncService;
import com.cinema.film_service.services.TypeService;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
import com.cinema.text.SearchTextUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TypeServiceImpl implements TypeService {

    private final FilmTypeRepository filmTypeRepository;
    private final FilmCatalogSyncService filmCatalogSyncService;

    @Override
    @Transactional
    public ActionMessageResponse createType(CreateFilmTypeRequest request, HttpServletRequest httpRequest) {
        validateAdmin(httpRequest, "createType");
        String name = normalize(request.getName());
        if (filmTypeRepository.findByNameIgnoreCaseAndIsDeletedFalse(name).isPresent()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        filmTypeRepository.save(FilmType.builder()
                .name(name)
                .build());

        return ActionMessageResponse.builder()
                .message(SuccessMessage.CREATED.getMessage())
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse updateType(UUID id, UpdateFilmTypeRequest request, HttpServletRequest httpRequest) {
        validateAdmin(httpRequest, "updateType");
        FilmType type = getActiveTypeOrThrow(id);
        String name = normalize(request.getName());
        if (filmTypeRepository.existsByNameIgnoreCaseAndIdNotAndIsDeletedFalse(name, id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        type.setName(name);
        filmTypeRepository.save(type);
        filmCatalogSyncService.refreshFilmsForType(id);

        return ActionMessageResponse.builder()
                .message(SuccessMessage.UPDATED.getMessage())
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse deleteType(UUID id, HttpServletRequest httpRequest) {
        validateAdmin(httpRequest, "deleteType");
        FilmType type = getActiveTypeOrThrow(id);
        type.setIsDeleted(true);
        filmTypeRepository.save(type);
        filmCatalogSyncService.refreshFilmsForType(id);

        return ActionMessageResponse.builder()
                .message(SuccessMessage.DELETED.getMessage())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public FilmTypeResponse getTypeById(UUID id) {
        return toResponse(getActiveTypeOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FilmTypeResponse> searchTypes(PageRequest<FilmTypeField> request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        List<FilmType> types = filmTypeRepository.findAllByIsDeletedFalseOrderByNameAsc();
        if (types.isEmpty()) {
            return emptyPageResponse(request);
        }

        List<FilmType> filtered = applySearchRequest(types, request);
        int page = request.getPageOrDefault();
        int size = request.getSizeOrDefault();
        long totalElements = filtered.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        List<FilmType> pageItems = paginate(filtered, page, size);
        List<FilmTypeResponse> data = pageItems.stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.<FilmTypeResponse>builder()
                .data(data)
                .currentPage(page)
                .totalPages(totalPages)
                .totalElements(totalElements)
                .size(size)
                .hasNext(page < totalPages)
                .hasPrevious(page > 1)
                .build();
    }

    private FilmType getActiveTypeOrThrow(UUID id) {
        return filmTypeRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private FilmTypeResponse toResponse(FilmType type) {
        return FilmTypeResponse.builder()
                .id(type.getId())
                .name(type.getName())
                .timeCreated(type.getTimeCreated())
                .timeUpdated(type.getTimeUpdated())
                .build();
    }

    private PageResponse<FilmTypeResponse> emptyPageResponse(PageRequest<FilmTypeField> request) {
        int page = request.getPageOrDefault();
        int size = request.getSizeOrDefault();
        return PageResponse.<FilmTypeResponse>builder()
                .data(List.of())
                .currentPage(page)
                .totalPages(0)
                .totalElements(0)
                .size(size)
                .hasNext(false)
                .hasPrevious(page > 1)
                .build();
    }

    private List<FilmType> applySearchRequest(List<FilmType> items, PageRequest<FilmTypeField> request) {
        List<FilmType> filtered = new ArrayList<>(items);

        String keyword = request.getNormalizedKeyword();
        if (StringUtils.hasText(keyword)) {
            filtered = filtered.stream()
                    .filter(item -> matchesKeyword(item, keyword))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        List<FilterField<FilmTypeField>> filters = request.getFilterBy();
        if (filters != null && !filters.isEmpty()) {
            filtered = filtered.stream()
                    .filter(item -> matchesAllFilters(item, filters))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        filtered.sort(buildComparator(request.getSortBy()));
        return filtered;
    }

    private Comparator<FilmType> buildComparator(List<SortField<FilmTypeField>> sortFields) {
        Comparator<FilmType> defaultComparator = Comparator
                .comparing(FilmType::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(FilmType::getTimeCreated, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(FilmType::getId, Comparator.nullsLast(Comparator.naturalOrder()));

        if (sortFields == null || sortFields.isEmpty()) {
            return defaultComparator;
        }

        Comparator<FilmType> merged = null;
        for (SortField<FilmTypeField> sort : sortFields) {
            if (sort == null || sort.getField() == null) {
                continue;
            }

            Comparator<FilmType> fieldComparator = (left, right) -> compareValues(
                    sort.getField().getFieldValue(left),
                    sort.getField().getFieldValue(right));
            if ("DESC".equalsIgnoreCase(sort.getDirection())) {
                fieldComparator = fieldComparator.reversed();
            }
            merged = merged == null ? fieldComparator : merged.thenComparing(fieldComparator);
        }

        return merged == null ? defaultComparator : merged;
    }

    private boolean matchesKeyword(FilmType type, String keyword) {
        if (!StringUtils.hasText(keyword) || type == null) {
            return true;
        }
        return SearchTextUtils.containsIgnoreCase(type.getName(), keyword);
    }

    private boolean matchesAllFilters(FilmType type, List<FilterField<FilmTypeField>> filters) {
        for (FilterField<FilmTypeField> filter : filters) {
            if (!matchesFilter(type, filter)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesFilter(FilmType type, FilterField<FilmTypeField> filter) {
        if (type == null || filter == null || filter.getField() == null || filter.getOperator() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        String operator = filter.getOperator().trim().toUpperCase(Locale.ROOT);
        Object rawValue = filter.getValue();
        if (rawValue == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        Object fieldValue = filter.getField().getFieldValue(type);
        Class<?> dataType = filter.getField().getDataType();

        return switch (operator) {
            case "EQ" -> compareValues(fieldValue, FilmTypeField.convertValue(String.valueOf(rawValue), dataType)) == 0;
            case "NEQ" -> compareValues(fieldValue, FilmTypeField.convertValue(String.valueOf(rawValue), dataType)) != 0;
            case "LIKE" -> fieldValue instanceof String text
                    && SearchTextUtils.containsIgnoreCase(text, String.valueOf(rawValue));
            case "GTE" -> compareValues(fieldValue, FilmTypeField.convertValue(String.valueOf(rawValue), dataType)) >= 0;
            case "LTE" -> compareValues(fieldValue, FilmTypeField.convertValue(String.valueOf(rawValue), dataType)) <= 0;
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
                values.add(FilmTypeField.convertValue(String.valueOf(item), dataType));
            }
            return values;
        }

        if (rawValue.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(rawValue);
            for (int i = 0; i < length; i++) {
                values.add(FilmTypeField.convertValue(String.valueOf(java.lang.reflect.Array.get(rawValue, i)), dataType));
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
            values.add(FilmTypeField.convertValue(value, dataType));
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

    private List<FilmType> paginate(List<FilmType> items, int page, int size) {
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

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return value.trim();
    }

    private void validateAdmin(HttpServletRequest httpRequest, String action) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_ADMIN, log, action);
    }
}
