package com.cinema.film_service.repository;

import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.SortField;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.film_service.dto.request.FilmField;
import com.cinema.film_service.entity.Film;
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

@RequiredArgsConstructor
@Repository
public class FilmRepositoryImpl {
    @PersistenceContext
    private final EntityManager entityManager;

    public List<Film> searchWithCursorAndSortAndFilter(
            String[] cursorParts,
            String keyword,
            int size,
            List<SortField<FilmField>> sortBy,
            List<FilterField<FilmField>> filterBy) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Film> cq = cb.createQuery(Film.class);
        Root<Film> root = cq.from(Film.class);

        List<Predicate> predicates = buildPredicates(cb, root, cursorParts, keyword, sortBy, filterBy, false);
        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(buildOrders(cb, root, sortBy));

        TypedQuery<Film> query = entityManager.createQuery(cq);
        query.setMaxResults(size + 1);
        return query.getResultList();
    }

    public List<Film> previousCursor(
            String[] cursorParts,
            String keyword,
            int size,
            List<SortField<FilmField>> sortBy,
            List<FilterField<FilmField>> filterBy) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Film> cq = cb.createQuery(Film.class);
        Root<Film> root = cq.from(Film.class);

        List<Predicate> predicates = buildPredicates(cb, root, cursorParts, keyword, sortBy, filterBy, true);
        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(buildOrders(cb, root, sortBy));

        TypedQuery<Film> query = entityManager.createQuery(cq);
        query.setMaxResults(size);
        return query.getResultList();
    }

    private List<Predicate> buildPredicates(
            CriteriaBuilder cb,
            Root<Film> root,
            String[] cursorParts,
            String keyword,
            List<SortField<FilmField>> sortBy,
            List<FilterField<FilmField>> filterBy,
            boolean previous) {
        List<Predicate> predicates = new ArrayList<>();

        if (cursorParts != null && sortBy != null && !sortBy.isEmpty() && cursorParts.length >= sortBy.size()) {
            List<Predicate> orPredicates = new ArrayList<>();
            int n = sortBy.size();

            for (int level = 0; level < n; level++) {
                List<Predicate> andPredicates = new ArrayList<>();

                for (int j = 0; j < level; j++) {
                    SortField<FilmField> prevSort = sortBy.get(j);
                    Comparable<?> eqValue = FilmField.convertValue(cursorParts[j], prevSort.getField().getDataType());
                    andPredicates.add(cb.equal(root.get(prevSort.getField().getEntityField()), eqValue));
                }

                SortField<FilmField> currentSort = sortBy.get(level);
                Comparable<?> cmpValue = FilmField.convertValue(cursorParts[level], currentSort.getField().getDataType());
                String fieldName = currentSort.getField().getEntityField();
                Path<? extends Comparable<?>> fieldPath = comparablePath(root, fieldName);

                boolean desc = "DESC".equalsIgnoreCase(currentSort.getDirection());
                Predicate cmpPredicate;
                if (previous) {
                    cmpPredicate = desc
                            ? greaterThanPredicate(cb, fieldPath, cmpValue)
                            : lessThanPredicate(cb, fieldPath, cmpValue);
                } else {
                    cmpPredicate = desc
                            ? lessThanPredicate(cb, fieldPath, cmpValue)
                            : greaterThanPredicate(cb, fieldPath, cmpValue);
                }

                andPredicates.add(cmpPredicate);
                orPredicates.add(cb.and(andPredicates.toArray(new Predicate[0])));
            }

            predicates.add(cb.or(orPredicates.toArray(new Predicate[0])));
        }

        Predicate keywordPredicate = buildKeywordPredicate(cb, root, keyword);
        if (keywordPredicate != null) {
            predicates.add(keywordPredicate);
        }

        if (filterBy != null) {
            for (FilterField<FilmField> filter : filterBy) {
                predicates.add(buildFilterPredicate(cb, root, filter));
            }
        }

        predicates.add(cb.isFalse(root.get(FilmField.IS_DELETED.getEntityField())));
        return predicates;
    }

    private Predicate buildFilterPredicate(CriteriaBuilder cb, Root<Film> root, FilterField<FilmField> filter) {
        if (filter == null || filter.getField() == null || filter.getOperator() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        String fieldName = filter.getField().getEntityField();
        Class<?> dataType = filter.getField().getDataType();
        String operator = filter.getOperator().trim().toUpperCase(Locale.ROOT);

        switch (operator) {
            case "EQ":
                return cb.equal(root.get(fieldName), convertSingleValue(filter.getValue(), dataType));
            case "NEQ":
                return cb.notEqual(root.get(fieldName), convertSingleValue(filter.getValue(), dataType));
            case "LIKE":
                return buildLikePredicate(cb, root, fieldName, dataType, filter.getValue());
            case "GTE":
                return buildComparePredicate(cb, root, fieldName, dataType, filter.getValue(), true);
            case "LTE":
                return buildComparePredicate(cb, root, fieldName, dataType, filter.getValue(), false);
            case "IN":
                return buildInPredicate(cb, root, fieldName, dataType, filter.getValue());
            case "BETWEEN":
                return buildBetweenPredicate(cb, root, fieldName, dataType, filter.getValue());
            default:
                throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private Predicate buildLikePredicate(
            CriteriaBuilder cb,
            Root<Film> root,
            String fieldName,
            Class<?> dataType,
            Object rawValue) {
        if (dataType != String.class) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (rawValue == null) {
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
            Root<Film> root,
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
            Root<Film> root,
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
            Root<Film> root,
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
        return FilmField.convertValue(String.valueOf(rawValue), dataType);
    }

    private List<Comparable<?>> convertMultipleValues(Object rawValue, Class<?> dataType) {
        if (rawValue == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        List<Comparable<?>> values = new ArrayList<>();
        if (rawValue instanceof Collection<?>) {
            Collection<?> collection = (Collection<?>) rawValue;
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
    private Path<? extends Comparable<?>> comparablePath(Root<Film> root, String fieldName) {
        return (Path<? extends Comparable<?>>) root.get(fieldName);
    }

    @SuppressWarnings("unchecked")
    private <T extends Comparable<? super T>> Predicate greaterThanOrEqualPredicate(
            CriteriaBuilder cb,
            Path<? extends Comparable<?>> path,
            Comparable<?> value) {
        return cb.greaterThanOrEqualTo((Path<T>) path, (T) value);
    }

    @SuppressWarnings("unchecked")
    private <T extends Comparable<? super T>> Predicate greaterThanPredicate(
            CriteriaBuilder cb,
            Path<? extends Comparable<?>> path,
            Comparable<?> value) {
        return cb.greaterThan((Path<T>) path, (T) value);
    }

    @SuppressWarnings("unchecked")
    private <T extends Comparable<? super T>> Predicate lessThanPredicate(
            CriteriaBuilder cb,
            Path<? extends Comparable<?>> path,
            Comparable<?> value) {
        return cb.lessThan((Path<T>) path, (T) value);
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

    private List<Order> buildOrders(CriteriaBuilder cb, Root<Film> root, List<SortField<FilmField>> sortBy) {
        List<Order> orders = new ArrayList<>();
        if (sortBy != null && !sortBy.isEmpty()) {
            for (SortField<FilmField> sort : sortBy) {
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

    private Predicate buildKeywordPredicate(CriteriaBuilder cb, Root<Film> root, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return null;
        }
        String keywordLower = keyword.toLowerCase();
        return cb.or(
                cb.like(cb.lower(root.get(FilmField.TITLE.getEntityField())), "%" + keywordLower + "%"),
                cb.like(cb.lower(root.get(FilmField.DIRECTOR.getEntityField())), "%" + keywordLower + "%"),
                cb.like(cb.lower(root.get(FilmField.ACTOR.getEntityField())), "%" + keywordLower + "%"),
                cb.like(cb.lower(root.get(FilmField.TYPE.getEntityField())), "%" + keywordLower + "%"),
                cb.like(cb.lower(root.get(FilmField.COUNTRY.getEntityField())), "%" + keywordLower + "%"),
                cb.like(cb.lower(root.get(FilmField.LANGUAGE.getEntityField())), "%" + keywordLower + "%"));
    }
}
