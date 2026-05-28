package com.cinema.booking_service.repository;

import com.cinema.booking_service.dto.request.BookingField;
import com.cinema.booking_service.entity.Booking;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.SortField;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RequiredArgsConstructor
@Repository
public class BookingRepositoryImpl {

    @PersistenceContext
    private final EntityManager entityManager;

    public List<Booking> searchWithPageAndSortAndFilter(
            UUID userId,
            String keyword,
            int page,
            int size,
            List<SortField<BookingField>> sortBy,
            List<FilterField<BookingField>> filterBy) {
        return searchWithPageAndSortAndFilter(userId, null, keyword, page, size, sortBy, filterBy);
    }

    public List<Booking> searchWithPageAndSortAndFilter(
            UUID userId,
            Collection<UUID> cinemaIds,
            String keyword,
            int page,
            int size,
            List<SortField<BookingField>> sortBy,
            List<FilterField<BookingField>> filterBy) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Booking> cq = cb.createQuery(Booking.class);
        Root<Booking> root = cq.from(Booking.class);

        List<Predicate> predicates = buildPredicates(cb, root, userId, cinemaIds, keyword, filterBy);
        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(buildOrders(cb, root, sortBy));

        TypedQuery<Booking> query = entityManager.createQuery(cq);
        query.setFirstResult(Math.max(0, (page - 1) * size));
        query.setMaxResults(size);
        return query.getResultList();
    }

    public long countWithFilter(
            UUID userId,
            String keyword,
            List<FilterField<BookingField>> filterBy) {
        return countWithFilter(userId, null, keyword, filterBy);
    }

    public long countWithFilter(
            UUID userId,
            Collection<UUID> cinemaIds,
            String keyword,
            List<FilterField<BookingField>> filterBy) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Booking> root = countQuery.from(Booking.class);

        List<Predicate> predicates = buildPredicates(cb, root, userId, cinemaIds, keyword, filterBy);
        countQuery.select(cb.count(root));
        countQuery.where(predicates.toArray(new Predicate[0]));
        return entityManager.createQuery(countQuery).getSingleResult();
    }

    private List<Predicate> buildPredicates(
            CriteriaBuilder cb,
            Root<Booking> root,
            UUID userId,
            Collection<UUID> cinemaIds,
            String keyword,
            List<FilterField<BookingField>> filterBy) {
        List<Predicate> predicates = new ArrayList<>();
        if (userId != null) {
            predicates.add(cb.equal(root.get(BookingField.USER_ID.getEntityField()), userId));
        }
        if (cinemaIds != null && !cinemaIds.isEmpty()) {
            predicates.add(root.get(BookingField.CINEMA_ID.getEntityField()).in(cinemaIds));
        }
        predicates.add(cb.isFalse(root.get("isDeleted")));

        Predicate keywordPredicate = buildKeywordPredicate(cb, root, keyword);
        if (keywordPredicate != null) {
            predicates.add(keywordPredicate);
        }

        if (filterBy != null) {
            for (FilterField<BookingField> filter : filterBy) {
                predicates.add(buildFilterPredicate(cb, root, filter));
            }
        }

        return predicates;
    }

    private Predicate buildFilterPredicate(
            CriteriaBuilder cb,
            Root<Booking> root,
            FilterField<BookingField> filter) {
        if (filter == null || filter.getField() == null || filter.getOperator() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        String fieldName = filter.getField().getEntityField();
        Class<?> dataType = filter.getField().getDataType();
        String operator = filter.getOperator().trim().toUpperCase(Locale.ROOT);

        return switch (operator) {
            case "EQ" -> cb.equal(root.get(fieldName), convertSingleValue(filter.getValue(), dataType));
            case "NEQ" -> cb.notEqual(root.get(fieldName), convertSingleValue(filter.getValue(), dataType));
            case "LIKE" -> buildLikePredicate(cb, root, fieldName, dataType, filter.getValue());
            case "GTE" -> buildComparePredicate(cb, root, fieldName, dataType, filter.getValue(), true);
            case "LTE" -> buildComparePredicate(cb, root, fieldName, dataType, filter.getValue(), false);
            case "IN" -> buildInPredicate(cb, root, fieldName, dataType, filter.getValue());
            case "BETWEEN" -> buildBetweenPredicate(cb, root, fieldName, dataType, filter.getValue());
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        };
    }

    private Predicate buildLikePredicate(
            CriteriaBuilder cb,
            Root<Booking> root,
            String fieldName,
            Class<?> dataType,
            Object rawValue) {
        if (dataType != String.class || rawValue == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        String value = String.valueOf(rawValue).trim();
        if (value.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return cb.like(cb.lower(root.get(fieldName).as(String.class)), "%" + value.toLowerCase(Locale.ROOT) + "%");
    }

    private Predicate buildComparePredicate(
            CriteriaBuilder cb,
            Root<Booking> root,
            String fieldName,
            Class<?> dataType,
            Object rawValue,
            boolean greaterOrEqual) {
        Comparable<?> value = convertSingleValue(rawValue, dataType);
        Path<? extends Comparable<?>> path = comparablePath(root, fieldName);
        if (greaterOrEqual) {
            return greaterThanOrEqualPredicate(cb, path, value);
        }
        return lessThanOrEqualPredicate(cb, path, value);
    }

    private Predicate buildInPredicate(
            CriteriaBuilder cb,
            Root<Booking> root,
            String fieldName,
            Class<?> dataType,
            Object rawValue) {
        List<Comparable<?>> values = convertMultipleValues(rawValue, dataType);
        if (values.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        CriteriaBuilder.In<Object> inClause = cb.in(root.get(fieldName));
        for (Comparable<?> value : values) {
            inClause.value(value);
        }
        return inClause;
    }

    private Predicate buildBetweenPredicate(
            CriteriaBuilder cb,
            Root<Booking> root,
            String fieldName,
            Class<?> dataType,
            Object rawValue) {
        List<Comparable<?>> values = convertMultipleValues(rawValue, dataType);
        if (values.size() != 2) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Comparable<?> start = values.get(0);
        Comparable<?> end = values.get(1);
        if (isGreaterThan(start, end)) {
            Comparable<?> tmp = start;
            start = end;
            end = tmp;
        }

        Path<? extends Comparable<?>> path = comparablePath(root, fieldName);
        return cb.and(
                greaterThanOrEqualPredicate(cb, path, start),
                lessThanOrEqualPredicate(cb, path, end));
    }

    private Comparable<?> convertSingleValue(Object rawValue, Class<?> dataType) {
        if (rawValue == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return BookingField.convertValue(String.valueOf(rawValue), dataType);
    }

    private List<Comparable<?>> convertMultipleValues(Object rawValue, Class<?> dataType) {
        if (rawValue == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        List<Comparable<?>> values = new ArrayList<>();
        if (rawValue instanceof Collection<?> collection) {
            for (Object item : collection) {
                values.add(convertSingleValue(item, dataType));
            }
            return values;
        }

        if (rawValue.getClass().isArray()) {
            int length = Array.getLength(rawValue);
            for (int i = 0; i < length; i++) {
                values.add(convertSingleValue(Array.get(rawValue, i), dataType));
            }
            return values;
        }

        String text = String.valueOf(rawValue).trim();
        if (text.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        for (String token : text.split(",")) {
            String value = token.trim();
            if (value.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            values.add(convertSingleValue(value, dataType));
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private Path<? extends Comparable<?>> comparablePath(Root<Booking> root, String fieldName) {
        return (Path<? extends Comparable<?>>) (Path<?>) root.get(fieldName);
    }

    @SuppressWarnings("unchecked")
    private <T extends Comparable<? super T>> Predicate greaterThanOrEqualPredicate(
            CriteriaBuilder cb,
            Path<? extends Comparable<?>> path,
            Comparable<?> value) {
        return cb.greaterThanOrEqualTo((Path<T>) path, (T) value);
    }

    @SuppressWarnings("unchecked")
    private <T extends Comparable<? super T>> Predicate lessThanOrEqualPredicate(
            CriteriaBuilder cb,
            Path<? extends Comparable<?>> path,
            Comparable<?> value) {
        return cb.lessThanOrEqualTo((Path<T>) path, (T) value);
    }

    private boolean isGreaterThan(Comparable<?> left, Comparable<?> right) {
        return asComparable(left).compareTo(right) > 0;
    }

    @SuppressWarnings("unchecked")
    private Comparable<Object> asComparable(Comparable<?> value) {
        return (Comparable<Object>) value;
    }

    private List<Order> buildOrders(
            CriteriaBuilder cb,
            Root<Booking> root,
            List<SortField<BookingField>> sortBy) {
        List<Order> orders = new ArrayList<>();
        if (sortBy != null && !sortBy.isEmpty()) {
            for (SortField<BookingField> sort : sortBy) {
                if (sort == null || sort.getField() == null) {
                    continue;
                }
                String field = sort.getField().getEntityField();
                if ("DESC".equalsIgnoreCase(sort.getDirection())) {
                    orders.add(cb.desc(root.get(field)));
                } else {
                    orders.add(cb.asc(root.get(field)));
                }
            }
        }
        return orders;
    }

    private Predicate buildKeywordPredicate(CriteriaBuilder cb, Root<Booking> root, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.like(root.get(BookingField.ID.getEntityField()).as(String.class), "%" + keyword + "%"));
        predicates.add(cb.like(root.get(BookingField.SHOWTIME_ID.getEntityField()).as(String.class), "%" + keyword + "%"));
        predicates.add(cb.like(root.get(BookingField.CINEMA_ID.getEntityField()).as(String.class), "%" + keyword + "%"));
        predicates.add(cb.like(
                cb.lower(root.get(BookingField.FILM_TITLE.getEntityField()).as(String.class)),
                "%" + keyword.toLowerCase(Locale.ROOT) + "%"));
        predicates.add(cb.like(
                cb.lower(root.get("customerInfo").get("fullName").as(String.class)),
                "%" + keyword.toLowerCase(Locale.ROOT) + "%"));
        predicates.add(cb.like(
                cb.lower(root.get("customerInfo").get("email").as(String.class)),
                "%" + keyword.toLowerCase(Locale.ROOT) + "%"));
        predicates.add(cb.like(root.get("customerInfo").get("phone").as(String.class), "%" + keyword + "%"));

        try {
            UUID keywordUuid = UUID.fromString(keyword);
            predicates.add(cb.equal(root.get(BookingField.ID.getEntityField()), keywordUuid));
            predicates.add(cb.equal(root.get(BookingField.SHOWTIME_ID.getEntityField()), keywordUuid));
            predicates.add(cb.equal(root.get(BookingField.CINEMA_ID.getEntityField()), keywordUuid));
        } catch (IllegalArgumentException ignored) {
        }

        return cb.or(predicates.toArray(new Predicate[0]));
    }
}
