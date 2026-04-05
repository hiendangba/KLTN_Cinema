package com.cinema.film_service.repository;

import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.SortField;
import com.cinema.film_service.dto.request.FilmField;
import com.cinema.film_service.entity.Film;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Repository
public class FilmRepositoryImpl {
    @PersistenceContext
    private final EntityManager entityManager;

    /**
     * Composite cursor-based pagination: expects cursorParts[0]=createdAt (String),
     * cursorParts[1]=id (UUID)
     * Always sorts by createdAt, then id as tie-breaker.
     */
    public List<Film> searchWithCursorAndSortAndFilter(
            String[] cursorParts,
            String keyword,
            int size,
            List<SortField<FilmField>> sortBy,
            List<FilterField<FilmField>> filterBy) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Film> cq = cb.createQuery(Film.class);
        Root<Film> root = cq.from(Film.class);

        List<Predicate> predicates = new ArrayList<>();
        if (cursorParts != null && sortBy != null && !sortBy.isEmpty() && cursorParts.length >= sortBy.size()) {
            List<Predicate> orPredicates = new ArrayList<>();
            int n = sortBy.size(); // Giả định sortBy ĐÃ bao gồm trường ID ở cuối

            for (int level = 0; level < n; level++) {
                List<Predicate> andPredicates = new ArrayList<>();

                // 1. Các trường trước đó phải BẰNG (Equal) giá trị cursor
                for (int j = 0; j < level; j++) {
                    SortField<FilmField> prevSort = sortBy.get(j);
                    Comparable eqValue = FilmField.convertValue(cursorParts[j], prevSort.getField().getDataType());
                    andPredicates.add(cb.equal(root.get(prevSort.getField().getEntityField()), eqValue));
                }

                // 2. Trường hiện tại phải LỚN HƠN hoặc NHỎ HƠN (tùy ASC/DESC)
                SortField<FilmField> currentSort = sortBy.get(level);
                Comparable cmpValue = FilmField.convertValue(cursorParts[level], currentSort.getField().getDataType());
                String fieldName = currentSort.getField().getEntityField();

                Predicate cmpPredicate;
                if ("DESC".equalsIgnoreCase(currentSort.getDirection())) {
                    cmpPredicate = cb.lessThan(root.get(fieldName), cmpValue);
                } else {
                    cmpPredicate = cb.greaterThan(root.get(fieldName), cmpValue);
                }

                andPredicates.add(cmpPredicate);

                // Kết hợp lại thành một cụm: (f1=v1 AND f2=v2 AND f3 > v3)
                orPredicates.add(cb.and(andPredicates.toArray(new Predicate[0])));
            }

            // Cuối cùng: predicates.add(cb.or(...))
            predicates.add(cb.or(orPredicates.toArray(new Predicate[0])));
        }

        Predicate keywordPredicate = buildKeywordPredicate(cb, root, keyword);
        if (keywordPredicate != null) {
            predicates.add(keywordPredicate);
        }

        if (filterBy != null) {
            for (FilterField<FilmField> filter : filterBy) {
                Class<?> dataType = filter.getField().getDataType();
                Comparable filterValue = FilmField.convertValue(filter.getValue().toString(), dataType);
                predicates.add(cb.equal(root.get(filter.getField().getEntityField()), filterValue));
            }
        }

        predicates.add(cb.isFalse(root.get(FilmField.IS_DELETED.getEntityField())));
        cq.where(predicates.toArray(new Predicate[0]));

        // Always ensure sortBy ends with id as tie-breaker
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
        cq.orderBy(orders);

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

        List<Predicate> predicates = new ArrayList<>();
        if (cursorParts != null && sortBy != null && !sortBy.isEmpty() && cursorParts.length >= sortBy.size()) {
            List<Predicate> orPredicates = new ArrayList<>();
            int n = sortBy.size();

            for (int level = 0; level < n; level++) {
                List<Predicate> andPredicates = new ArrayList<>();

                // 1. Các trường trước đó phải BẰNG
                for (int j = 0; j < level; j++) {
                    SortField<FilmField> prevSort = sortBy.get(j);
                    Comparable eqValue = FilmField.convertValue(cursorParts[j], prevSort.getField().getDataType());
                    andPredicates.add(cb.equal(root.get(prevSort.getField().getEntityField()), eqValue));
                }

                // 2. Trường hiện tại ĐẢO DẤU so với hàm Next
                SortField<FilmField> currentSort = sortBy.get(level);
                Comparable cmpValue = FilmField.convertValue(cursorParts[level], currentSort.getField().getDataType());
                String fieldName = currentSort.getField().getEntityField();

                Predicate cmpPredicate;
                // Đảo ngược logic:
                // Nếu Next dùng DESC (<) thì Previous dùng (>)
                // Nếu Next dùng ASC (>) thì Previous dùng (<)
                if ("DESC".equalsIgnoreCase(currentSort.getDirection())) {
                    cmpPredicate = cb.greaterThan(root.get(fieldName), cmpValue);
                } else {
                    cmpPredicate = cb.lessThan(root.get(fieldName), cmpValue);
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
                Class<?> dataType = filter.getField().getDataType();
                Comparable filterValue = FilmField.convertValue(filter.getValue().toString(), dataType);
                predicates.add(cb.equal(root.get(filter.getField().getEntityField()), filterValue));
            }
        }

        predicates.add(cb.isFalse(root.get(FilmField.IS_DELETED.getEntityField())));
        cq.where(predicates.toArray(new Predicate[0]));

        // Always ensure sortBy ends with id as tie-breaker
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

        if (!hasIdSort(sortBy)) {
            orders.add(cb.asc(root.get(FilmField.ID.getEntityField())));
        }
        cq.orderBy(orders);

        TypedQuery<Film> query = entityManager.createQuery(cq);
        query.setMaxResults(size);

        return query.getResultList();
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

    private boolean hasIdSort(List<SortField<FilmField>> sortBy) {
        if (sortBy == null || sortBy.isEmpty()) {
            return false;
        }
        for (SortField<FilmField> sort : sortBy) {
            if (sort.getField() == FilmField.ID) {
                return true;
            }
        }
        return false;
    }
}
