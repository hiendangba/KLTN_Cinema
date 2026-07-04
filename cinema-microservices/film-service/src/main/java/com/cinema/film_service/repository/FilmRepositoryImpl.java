package com.cinema.film_service.repository;

import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.SortField;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.film_service.dto.request.FilmField;
import com.cinema.film_service.entity.Actor;
import com.cinema.film_service.entity.Film;
import com.cinema.film_service.entity.FilmType;
import com.cinema.text.SearchTextUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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

        List<Predicate> predicates = buildPredicates(cb, cq, root, cursorParts, keyword, sortBy, filterBy, false);
        cq.where(predicates.toArray(new Predicate[0]));
        cq.distinct(true);
        cq.orderBy(buildOrders(cb, cq, root, sortBy));

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

        List<Predicate> predicates = buildPredicates(cb, cq, root, cursorParts, keyword, sortBy, filterBy, true);
        cq.where(predicates.toArray(new Predicate[0]));
        cq.distinct(true);
        cq.orderBy(buildOrders(cb, cq, root, sortBy));

        TypedQuery<Film> query = entityManager.createQuery(cq);
        query.setMaxResults(size);
        return query.getResultList();
    }

    private List<Predicate> buildPredicates(
            CriteriaBuilder cb,
            CriteriaQuery<Film> cq,
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
                    andPredicates.add(buildFieldEqualityPredicate(cb, cq, root, prevSort.getField(), eqValue));
                }

                SortField<FilmField> currentSort = sortBy.get(level);
                Comparable<?> cmpValue = FilmField.convertValue(cursorParts[level], currentSort.getField().getDataType());
                Predicate cmpPredicate = buildCursorComparePredicate(cb, cq, root, currentSort, cmpValue, previous);
                andPredicates.add(cmpPredicate);
                orPredicates.add(cb.and(andPredicates.toArray(new Predicate[0])));
            }

            predicates.add(cb.or(orPredicates.toArray(new Predicate[0])));
        }

        Predicate keywordPredicate = buildKeywordPredicate(cb, cq, root, keyword);
        if (keywordPredicate != null) {
            predicates.add(keywordPredicate);
        }

        if (filterBy != null) {
            for (FilterField<FilmField> filter : filterBy) {
                predicates.add(buildFilterPredicate(cb, cq, root, filter));
            }
        }

        predicates.add(cb.isFalse(root.get(FilmField.IS_DELETED.getEntityField())));
        return predicates;
    }

    private Predicate buildFieldEqualityPredicate(
            CriteriaBuilder cb,
            CriteriaQuery<Film> cq,
            Root<Film> root,
            FilmField field,
            Comparable<?> value) {
        if (field == FilmField.ACTOR || field == FilmField.TYPE) {
            return buildRelationPredicate(cb, cq, root, field, "EQ", value);
        }
        return cb.equal(root.get(field.getEntityField()), value);
    }

    private Predicate buildCursorComparePredicate(
            CriteriaBuilder cb,
            CriteriaQuery<Film> cq,
            Root<Film> root,
            SortField<FilmField> sortField,
            Comparable<?> value,
            boolean previous) {
        FilmField field = sortField.getField();
        boolean desc = "DESC".equalsIgnoreCase(sortField.getDirection());

        if (field == FilmField.ACTOR || field == FilmField.TYPE) {
            Expression<String> expression = relationSortExpression(cb, cq, root, field);
            return compareCursorExpression(cb, expression, value, desc, previous);
        }

        Path<? extends Comparable<?>> path = comparablePath(root, field.getEntityField());
        return compareCursorExpression(cb, path, value, desc, previous);
    }

    private Predicate compareCursorExpression(
            CriteriaBuilder cb,
            Expression<String> expression,
            Comparable<?> value,
            boolean desc,
            boolean previous) {
        String normalized = value == null ? null : String.valueOf(value);
        if (normalized == null) {
            return cb.conjunction();
        }

        String compareValue = normalized.toLowerCase(Locale.ROOT);
        Expression<String> left = cb.lower(expression);
        return previous
                ? (desc ? cb.greaterThan(left, compareValue) : cb.lessThan(left, compareValue))
                : (desc ? cb.lessThan(left, compareValue) : cb.greaterThan(left, compareValue));
    }

    @SuppressWarnings("unchecked")
    private Predicate compareCursorExpression(
            CriteriaBuilder cb,
            Path<? extends Comparable<?>> path,
            Comparable<?> value,
            boolean desc,
            boolean previous) {
        if (previous) {
            return desc
                    ? greaterThanPredicate(cb, path, value)
                    : lessThanPredicate(cb, path, value);
        }
        return desc
                ? lessThanPredicate(cb, path, value)
                : greaterThanPredicate(cb, path, value);
    }

    private Predicate buildFilterPredicate(CriteriaBuilder cb, CriteriaQuery<Film> cq, Root<Film> root, FilterField<FilmField> filter) {
        if (filter == null || filter.getField() == null || filter.getOperator() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        FilmField field = filter.getField();
        String operator = filter.getOperator().trim().toUpperCase(Locale.ROOT);
        Object rawValue = filter.getValue();
        if (rawValue == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (field == FilmField.ACTOR || field == FilmField.TYPE) {
            return buildRelationPredicate(cb, cq, root, field, operator, rawValue);
        }

        String fieldName = field.getEntityField();
        Class<?> dataType = field.getDataType();

        switch (operator) {
            case "EQ":
                return cb.equal(root.get(fieldName), convertSingleValue(rawValue, dataType));
            case "NEQ":
                return cb.notEqual(root.get(fieldName), convertSingleValue(rawValue, dataType));
            case "LIKE":
                return buildLikePredicate(cb, root, fieldName, dataType, rawValue);
            case "GTE":
                return buildComparePredicate(cb, root, fieldName, dataType, rawValue, true);
            case "LTE":
                return buildComparePredicate(cb, root, fieldName, dataType, rawValue, false);
            case "IN":
                return buildInPredicate(cb, root, fieldName, dataType, rawValue);
            case "BETWEEN":
                return buildBetweenPredicate(cb, root, fieldName, dataType, rawValue);
            default:
                throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private Predicate buildRelationPredicate(
            CriteriaBuilder cb,
            CriteriaQuery<Film> cq,
            Root<Film> root,
            FilmField field,
            String operator,
            Object rawValue) {
        String relationName = field == FilmField.ACTOR ? "actors" : "types";
        String attributeName = "name";
        Class<?> dataType = String.class;

        if ("NEQ".equals(operator)) {
            return cb.not(buildRelationExistsPredicate(cb, cq, root, relationName, attributeName, "EQ", rawValue, dataType));
        }

        return buildRelationExistsPredicate(cb, cq, root, relationName, attributeName, operator, rawValue, dataType);
    }

    private Predicate buildRelationExistsPredicate(
            CriteriaBuilder cb,
            CriteriaQuery<Film> cq,
            Root<Film> root,
            String relationName,
            String attributeName,
            String operator,
            Object rawValue,
            Class<?> dataType) {
        Subquery<Integer> subquery = cq.subquery(Integer.class);
        Root<Film> correlatedFilm = subquery.correlate(root);
        Join<Film, ?> join = correlatedFilm.join(relationName, JoinType.LEFT);
        join.on(cb.isFalse(join.get("isDeleted").as(Boolean.class)));
        Path<?> relationPath = join.get(attributeName);

        Predicate inner;
        switch (operator) {
            case "EQ":
                inner = cb.equal(cb.lower(relationPath.as(String.class)),
                        String.valueOf(convertSingleValue(rawValue, dataType)).toLowerCase(Locale.ROOT));
                break;
            case "LIKE":
                inner = SearchTextUtils.accentInsensitiveLike(cb, relationPath, String.valueOf(rawValue));
                break;
            case "GTE":
                inner = buildRelationComparePredicate(cb, relationPath, rawValue, dataType, true);
                break;
            case "LTE":
                inner = buildRelationComparePredicate(cb, relationPath, rawValue, dataType, false);
                break;
            case "IN":
                inner = buildRelationInPredicate(cb, relationPath, rawValue, dataType);
                break;
            case "BETWEEN":
                inner = buildRelationBetweenPredicate(cb, relationPath, rawValue, dataType);
                break;
            default:
                throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        subquery.select(cb.literal(1)).where(inner);
        return cb.exists(subquery);
    }

    private Predicate buildRelationComparePredicate(
            CriteriaBuilder cb,
            Path<?> path,
            Object rawValue,
            Class<?> dataType,
            boolean greaterOrEqual) {
        String value = String.valueOf(convertSingleValue(rawValue, dataType)).toLowerCase(Locale.ROOT);
        @SuppressWarnings("unchecked")
        Path<String> stringPath = (Path<String>) path.as(String.class);
        return greaterOrEqual
                ? cb.greaterThanOrEqualTo(cb.lower(stringPath), value)
                : cb.lessThanOrEqualTo(cb.lower(stringPath), value);
    }

    private Predicate buildRelationInPredicate(
            CriteriaBuilder cb,
            Path<?> path,
            Object rawValue,
            Class<?> dataType) {
        List<Comparable<?>> values = convertMultipleValues(rawValue, dataType);
        if (values.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        @SuppressWarnings("unchecked")
        Path<String> stringPath = (Path<String>) path.as(String.class);
        CriteriaBuilder.In<String> inClause = cb.in(cb.lower(stringPath));
        for (Comparable<?> value : values) {
            inClause.value(String.valueOf(value).toLowerCase(Locale.ROOT));
        }
        return inClause;
    }

    private Predicate buildRelationBetweenPredicate(
            CriteriaBuilder cb,
            Path<?> path,
            Object rawValue,
            Class<?> dataType) {
        List<Comparable<?>> values = convertMultipleValues(rawValue, dataType);
        if (values.size() != 2) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        String start = String.valueOf(values.get(0)).toLowerCase(Locale.ROOT);
        String end = String.valueOf(values.get(1)).toLowerCase(Locale.ROOT);
        if (start.compareTo(end) > 0) {
            String tmp = start;
            start = end;
            end = tmp;
        }

        @SuppressWarnings("unchecked")
        Path<String> stringPath = (Path<String>) path.as(String.class);
        Expression<String> lower = cb.lower(stringPath);
        return cb.and(
                cb.greaterThanOrEqualTo(lower, start),
                cb.lessThanOrEqualTo(lower, end));
    }

    private Predicate buildKeywordPredicate(CriteriaBuilder cb, CriteriaQuery<Film> cq, Root<Film> root, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return cb.or(
                SearchTextUtils.accentInsensitiveLike(cb, root.get(FilmField.TITLE.getEntityField()), keyword),
                SearchTextUtils.accentInsensitiveLike(cb, root.get(FilmField.DIRECTOR.getEntityField()), keyword),
                SearchTextUtils.accentInsensitiveLike(cb, root.get(FilmField.COUNTRY.getEntityField()), keyword),
                SearchTextUtils.accentInsensitiveLike(cb, root.get(FilmField.LANGUAGE.getEntityField()), keyword),
                buildRelationExistsPredicate(cb, cq, root, "actors", "name", "LIKE", keyword, String.class),
                buildRelationExistsPredicate(cb, cq, root, "types", "name", "LIKE", keyword, String.class));
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

    private List<Order> buildOrders(CriteriaBuilder cb, CriteriaQuery<Film> cq, Root<Film> root, List<SortField<FilmField>> sortBy) {
        List<Order> orders = new ArrayList<>();
        if (sortBy != null && !sortBy.isEmpty()) {
            for (SortField<FilmField> sort : sortBy) {
                FilmField field = sort.getField();
                boolean desc = "DESC".equalsIgnoreCase(sort.getDirection());
                if (field == FilmField.ACTOR || field == FilmField.TYPE) {
                    Expression<String> expression = relationSortExpression(cb, cq, root, field);
                    orders.add(desc ? cb.desc(expression) : cb.asc(expression));
                    continue;
                }

                String entityField = field.getEntityField();
                if (desc) {
                    orders.add(cb.desc(root.get(entityField)));
                } else {
                    orders.add(cb.asc(root.get(entityField)));
                }
            }
        }
        return orders;
    }

    private Expression<String> relationSortExpression(CriteriaBuilder cb, CriteriaQuery<Film> cq, Root<Film> root, FilmField field) {
        String relationName = field == FilmField.ACTOR ? "actors" : "types";
        Subquery<String> subquery = cq.subquery(String.class);
        Root<Film> correlatedFilm = subquery.correlate(root);
        Join<Film, ?> join = correlatedFilm.join(relationName, JoinType.LEFT);
        join.on(cb.isFalse(join.get("isDeleted").as(Boolean.class)));
        subquery.select(cb.least(cb.lower(join.get("name").as(String.class))));
        return subquery;
    }
}
