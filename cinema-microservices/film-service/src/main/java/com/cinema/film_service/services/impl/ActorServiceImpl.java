package com.cinema.film_service.services.impl;

import com.cinema.Enum.SuccessMessage;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.request.SortField;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.film_service.dto.request.ActorField;
import com.cinema.film_service.dto.request.CreateActorRequest;
import com.cinema.film_service.dto.request.UpdateActorRequest;
import com.cinema.film_service.dto.response.ActorResponse;
import com.cinema.film_service.entity.Actor;
import com.cinema.film_service.repository.ActorRepository;
import com.cinema.film_service.services.ActorService;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
import com.cinema.text.SearchTextUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
public class ActorServiceImpl implements ActorService {

    private final ActorRepository actorRepository;

    @Override
    @Transactional
    public ActionMessageResponse createActor(CreateActorRequest request, HttpServletRequest httpRequest) {
        validateAdmin(httpRequest, "createActor");
        String name = normalize(request.getName());
        if (actorRepository.existsByNameIgnoreCaseAndIsDeletedFalse(name)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        Actor actor = Actor.builder()
                .name(name)
                .birthYear(request.getBirthYear())
                .hometown(normalizeOptional(request.getHometown()))
                .avatarUrl(normalizeOptional(request.getAvatarUrl()))
                .build();
        actorRepository.save(actor);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.CREATED.getMessage())
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse updateActor(UUID id, UpdateActorRequest request, HttpServletRequest httpRequest) {
        validateAdmin(httpRequest, "updateActor");
        Actor actor = getActiveActorOrThrow(id);
        actor.setName(normalize(request.getName()));
        if (actorRepository.existsByNameIgnoreCaseAndIdNotAndIsDeletedFalse(actor.getName(), id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        actor.setBirthYear(request.getBirthYear());
        actor.setHometown(normalizeOptional(request.getHometown()));
        actor.setAvatarUrl(normalizeOptional(request.getAvatarUrl()));
        actorRepository.save(actor);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.UPDATED.getMessage())
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse deleteActor(UUID id, HttpServletRequest httpRequest) {
        validateAdmin(httpRequest, "deleteActor");
        Actor actor = getActiveActorOrThrow(id);
        actor.setIsDeleted(true);
        actorRepository.save(actor);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.DELETED.getMessage())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ActorResponse getActorById(UUID id) {
        return toResponse(getActiveActorOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ActorResponse> searchActors(PageRequest<ActorField> request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        List<Actor> actors = actorRepository.findAllByIsDeletedFalseOrderByNameAsc();
        if (actors.isEmpty()) {
            return emptyPageResponse(request);
        }

        List<Actor> filtered = applySearchRequest(actors, request);
        int page = request.getPageOrDefault();
        int size = request.getSizeOrDefault();
        long totalElements = filtered.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        List<Actor> pageItems = paginate(filtered, page, size);
        List<ActorResponse> data = pageItems.stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.<ActorResponse>builder()
                .data(data)
                .currentPage(page)
                .totalPages(totalPages)
                .totalElements(totalElements)
                .size(size)
                .hasNext(page < totalPages)
                .hasPrevious(page > 1)
                .build();
    }

    private Actor getActiveActorOrThrow(UUID id) {
        return actorRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private ActorResponse toResponse(Actor actor) {
        return ActorResponse.builder()
                .id(actor.getId())
                .name(actor.getName())
                .birthYear(actor.getBirthYear())
                .hometown(actor.getHometown())
                .avatarUrl(actor.getAvatarUrl())
                .timeCreated(actor.getTimeCreated())
                .timeUpdated(actor.getTimeUpdated())
                .build();
    }

    private PageResponse<ActorResponse> emptyPageResponse(PageRequest<ActorField> request) {
        int page = request.getPageOrDefault();
        int size = request.getSizeOrDefault();
        return PageResponse.<ActorResponse>builder()
                .data(List.of())
                .currentPage(page)
                .totalPages(0)
                .totalElements(0)
                .size(size)
                .hasNext(false)
                .hasPrevious(page > 1)
                .build();
    }

    private List<Actor> applySearchRequest(List<Actor> items, PageRequest<ActorField> request) {
        List<Actor> filtered = new ArrayList<>(items);

        String keyword = request.getNormalizedKeyword();
        if (StringUtils.hasText(keyword)) {
            filtered = filtered.stream()
                    .filter(item -> matchesKeyword(item, keyword))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        List<FilterField<ActorField>> filters = request.getFilterBy();
        if (filters != null && !filters.isEmpty()) {
            filtered = filtered.stream()
                    .filter(item -> matchesAllFilters(item, filters))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        filtered.sort(buildComparator(request.getSortBy()));
        return filtered;
    }

    private Comparator<Actor> buildComparator(List<SortField<ActorField>> sortFields) {
        Comparator<Actor> defaultComparator = Comparator
                .comparing(Actor::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(Actor::getTimeCreated, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Actor::getId, Comparator.nullsLast(Comparator.naturalOrder()));

        if (sortFields == null || sortFields.isEmpty()) {
            return defaultComparator;
        }

        Comparator<Actor> merged = null;
        for (SortField<ActorField> sort : sortFields) {
            if (sort == null || sort.getField() == null) {
                continue;
            }

            Comparator<Actor> fieldComparator = (left, right) -> compareValues(
                    sort.getField().getFieldValue(left),
                    sort.getField().getFieldValue(right));
            if ("DESC".equalsIgnoreCase(sort.getDirection())) {
                fieldComparator = fieldComparator.reversed();
            }
            merged = merged == null ? fieldComparator : merged.thenComparing(fieldComparator);
        }

        return merged == null ? defaultComparator : merged;
    }

    private boolean matchesKeyword(Actor actor, String keyword) {
        if (!StringUtils.hasText(keyword) || actor == null) {
            return true;
        }
        return SearchTextUtils.containsIgnoreCase(actor.getName(), keyword)
                || SearchTextUtils.containsIgnoreCase(actor.getBirthYear() == null ? null : actor.getBirthYear().toString(), keyword)
                || SearchTextUtils.containsIgnoreCase(actor.getHometown(), keyword)
                || SearchTextUtils.containsIgnoreCase(actor.getAvatarUrl(), keyword);
    }

    private boolean matchesAllFilters(Actor actor, List<FilterField<ActorField>> filters) {
        for (FilterField<ActorField> filter : filters) {
            if (!matchesFilter(actor, filter)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesFilter(Actor actor, FilterField<ActorField> filter) {
        if (actor == null || filter == null || filter.getField() == null || filter.getOperator() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        String operator = filter.getOperator().trim().toUpperCase(Locale.ROOT);
        Object rawValue = filter.getValue();
        if (rawValue == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        Object fieldValue = filter.getField().getFieldValue(actor);
        Class<?> dataType = filter.getField().getDataType();

        return switch (operator) {
            case "EQ" -> compareValues(fieldValue, ActorField.convertValue(String.valueOf(rawValue), dataType)) == 0;
            case "NEQ" -> compareValues(fieldValue, ActorField.convertValue(String.valueOf(rawValue), dataType)) != 0;
            case "LIKE" -> fieldValue instanceof String text
                    && SearchTextUtils.containsIgnoreCase(text, String.valueOf(rawValue));
            case "GTE" -> compareValues(fieldValue, ActorField.convertValue(String.valueOf(rawValue), dataType)) >= 0;
            case "LTE" -> compareValues(fieldValue, ActorField.convertValue(String.valueOf(rawValue), dataType)) <= 0;
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
                values.add(ActorField.convertValue(String.valueOf(item), dataType));
            }
            return values;
        }

        if (rawValue.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(rawValue);
            for (int i = 0; i < length; i++) {
                values.add(ActorField.convertValue(String.valueOf(java.lang.reflect.Array.get(rawValue, i)), dataType));
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
            values.add(ActorField.convertValue(value, dataType));
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

    private List<Actor> paginate(List<Actor> items, int page, int size) {
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

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateAdmin(HttpServletRequest httpRequest, String action) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_ADMIN, log, action);
    }
}
