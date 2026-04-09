package com.cinema.hall_services.repository;

import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.SortField;
import com.cinema.hall_services.dto.request.HallField;
import com.cinema.hall_services.entity.Hall;
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
public class HallRepositoryImpl {
    @PersistenceContext
    private final EntityManager entityManager;

    public List<Hall> searchWithCursorAndSortAndFilter(
            String[] cursorParts,
            String keyword,
            int size,
            List<SortField<HallField>> sortBy,
            List<FilterField<HallField>> filterBy) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Hall> cq = cb.createQuery(Hall.class);
        Root<Hall> root = cq.from(Hall.class);

        List<Predicate> predicates = buildPredicates(cb, root, cursorParts, keyword, sortBy, filterBy, false);
        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(buildOrders(cb, root, sortBy));

        TypedQuery<Hall> query = entityManager.createQuery(cq);
        query.setMaxResults(size + 1);
        return query.getResultList();
    }

    public List<Hall> previousCursor(
            String[] cursorParts,
            String keyword,
            int size,
            List<SortField<HallField>> sortBy,
            List<FilterField<HallField>> filterBy) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Hall> cq = cb.createQuery(Hall.class);
        Root<Hall> root = cq.from(Hall.class);

        List<Predicate> predicates = buildPredicates(cb, root, cursorParts, keyword, sortBy, filterBy, true);
        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(buildOrders(cb, root, sortBy));

        TypedQuery<Hall> query = entityManager.createQuery(cq);
        query.setMaxResults(size);
        return query.getResultList();
    }

    private List<Predicate> buildPredicates(
            CriteriaBuilder cb,
            Root<Hall> root,
            String[] cursorParts,
            String keyword,
            List<SortField<HallField>> sortBy,
            List<FilterField<HallField>> filterBy,
            boolean previous) {
        List<Predicate> predicates = new ArrayList<>();

        if (cursorParts != null && sortBy != null && !sortBy.isEmpty() && cursorParts.length >= sortBy.size()) {
            List<Predicate> orPredicates = new ArrayList<>();
            int n = sortBy.size();

            for (int level = 0; level < n; level++) {
                List<Predicate> andPredicates = new ArrayList<>();

                for (int j = 0; j < level; j++) {
                    SortField<HallField> prevSort = sortBy.get(j);
                    Comparable<?> eqValue = HallField.convertValue(cursorParts[j], prevSort.getField().getDataType());
                    andPredicates.add(cb.equal(root.get(prevSort.getField().getEntityField()), eqValue));
                }

                SortField<HallField> currentSort = sortBy.get(level);
                Comparable<?> cmpValue = HallField.convertValue(cursorParts[level], currentSort.getField().getDataType());
                String fieldName = currentSort.getField().getEntityField();

                boolean desc = "DESC".equalsIgnoreCase(currentSort.getDirection());
                Predicate cmpPredicate;
                if (previous) {
                    cmpPredicate = desc
                            ? cb.greaterThan(root.get(fieldName), (Comparable) cmpValue)
                            : cb.lessThan(root.get(fieldName), (Comparable) cmpValue);
                } else {
                    cmpPredicate = desc
                            ? cb.lessThan(root.get(fieldName), (Comparable) cmpValue)
                            : cb.greaterThan(root.get(fieldName), (Comparable) cmpValue);
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
            for (FilterField<HallField> filter : filterBy) {
                Class<?> dataType = filter.getField().getDataType();
                Comparable<?> filterValue = HallField.convertValue(filter.getValue().toString(), dataType);
                predicates.add(cb.equal(root.get(filter.getField().getEntityField()), filterValue));
            }
        }

        predicates.add(cb.isFalse(root.get(HallField.IS_DELETED.getEntityField())));
        return predicates;
    }

    private List<Order> buildOrders(CriteriaBuilder cb, Root<Hall> root, List<SortField<HallField>> sortBy) {
        List<Order> orders = new ArrayList<>();
        if (sortBy != null && !sortBy.isEmpty()) {
            for (SortField<HallField> sort : sortBy) {
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

    private Predicate buildKeywordPredicate(CriteriaBuilder cb, Root<Hall> root, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.like(cb.lower(root.get(HallField.NAME.getEntityField())), "%" + keyword.toLowerCase() + "%"));
        predicates.add(cb.like(root.get(HallField.CINEMA_ID.getEntityField()).as(String.class), "%" + keyword + "%"));

        try {
            UUID keywordUuid = UUID.fromString(keyword);
            predicates.add(cb.equal(root.get(HallField.ID.getEntityField()), keywordUuid));
            predicates.add(cb.equal(root.get(HallField.CINEMA_ID.getEntityField()), keywordUuid));
        } catch (IllegalArgumentException ignored) {
        }

        return cb.or(predicates.toArray(new Predicate[0]));
    }
}
