package com.cinema.showtime_service.repository;

import com.cinema.Enum.ShowTimeEnum;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.SortField;
import com.cinema.showtime_service.dto.request.ShowTimeField;
import com.cinema.showtime_service.entity.ShowTime;
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

@RequiredArgsConstructor
@Repository
public class ShowTimeRepositoryImpl {
    @PersistenceContext
    private final EntityManager entityManager;

    public List<ShowTime> searchWithCursorAndSortAndFilter(
            String[] cursorParts,
            String keyword,
            int size,
            List<SortField<ShowTimeField>> sortBy,
            List<FilterField<ShowTimeField>> filterBy) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<ShowTime> cq = cb.createQuery(ShowTime.class);
        Root<ShowTime> root = cq.from(ShowTime.class);

        List<Predicate> predicates = new ArrayList<>();
        if (cursorParts != null && sortBy != null && !sortBy.isEmpty()) {
            List<Predicate> orPredicates = new ArrayList<>();
            int n = sortBy.size(); // Giả định sortBy ĐÃ bao gồm trường ID ở cuối

            for (int level = 0; level < n; level++) {
                List<Predicate> andPredicates = new ArrayList<>();

                // 1. Các trường trước đó phải BẰNG (Equal) giá trị cursor
                for (int j = 0; j < level; j++) {
                    SortField<ShowTimeField> prevSort = sortBy.get(j);
                    Comparable eqValue = ShowTimeField.convertValue(cursorParts[j], prevSort.getField().getDataType());
                    andPredicates.add(cb.equal(root.get(prevSort.getField().getEntityField()), eqValue));
                }

                // 2. Trường hiện tại phải LỚN HƠN hoặc NHỎ HƠN (tùy ASC/DESC)
                SortField<ShowTimeField> currentSort = sortBy.get(level);
                Comparable cmpValue = ShowTimeField.convertValue(cursorParts[level], currentSort.getField().getDataType());
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

        if (keyword != null && !keyword.isEmpty()) {
            Predicate keywordPredicate = cb.or(
                    cb.like(root.get(ShowTimeField.HALL_ID.getEntityField()).as(String.class), "%" + keyword + "%"),
                    cb.like(root.get(ShowTimeField.FILM_ID.getEntityField()).as(String.class), "%" + keyword + "%"));
            predicates.add(keywordPredicate);
        }

        Predicate equalPredicate = cb.or(
                cb.equal(root.get(ShowTimeField.STATUS.getEntityField()).as(String.class), ShowTimeEnum.ShowTimeStatus.SCHEDULED.toString()),
                cb.equal(root.get(ShowTimeField.STATUS.getEntityField()).as(String.class), ShowTimeEnum.ShowTimeStatus.ONGOING.toString())
        );
        predicates.add(equalPredicate);

        if (filterBy != null) {
            for (FilterField<ShowTimeField> filter : filterBy) {
                Class<?> dataType = filter.getField().getDataType();
                Comparable filterValue = ShowTimeField.convertValue(filter.getValue().toString(), dataType);
                predicates.add(cb.equal(root.get(filter.getField().getEntityField()), filterValue));
            }
        }

        predicates.add(cb.isFalse(root.get(ShowTimeField.IS_DELETED.getEntityField())));
        cq.where(predicates.toArray(new Predicate[0]));

        // Always ensure sortBy ends with id as tie-breaker
        List<Order> orders = new ArrayList<>();
        if (sortBy != null && !sortBy.isEmpty()) {
            for (SortField<ShowTimeField> sort : sortBy) {
                String field = sort.getField().getEntityField();
                if ("DESC".equalsIgnoreCase(sort.getDirection())) {
                    orders.add(cb.desc(root.get(field)));
                } else {
                    orders.add(cb.asc(root.get(field)));
                }
            }
        }
        cq.orderBy(orders);

        TypedQuery<ShowTime> query = entityManager.createQuery(cq);
        query.setMaxResults(size + 1);

        return query.getResultList();
    }

    public List<ShowTime> previousCursor(
            String[] cursorParts,
            String keyword,
            int size,
            List<SortField<ShowTimeField>> sortBy,
            List<FilterField<ShowTimeField>> filterBy) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<ShowTime> cq = cb.createQuery(ShowTime.class);
        Root<ShowTime> root = cq.from(ShowTime.class);

        List<Predicate> predicates = new ArrayList<>();
        if (cursorParts != null && sortBy != null && !sortBy.isEmpty()) {
            List<Predicate> orPredicates = new ArrayList<>();
            int n = sortBy.size();

            for (int level = 0; level < n; level++) {
                List<Predicate> andPredicates = new ArrayList<>();

                // 1. Các trường trước đó phải BẰNG
                for (int j = 0; j < level; j++) {
                    SortField<ShowTimeField> prevSort = sortBy.get(j);
                    Comparable eqValue = ShowTimeField.convertValue(cursorParts[j], prevSort.getField().getDataType());
                    andPredicates.add(cb.equal(root.get(prevSort.getField().getEntityField()), eqValue));
                }

                // 2. Trường hiện tại ĐẢO DẤU so với hàm Next
                SortField<ShowTimeField> currentSort = sortBy.get(level);
                Comparable cmpValue = ShowTimeField.convertValue(cursorParts[level], currentSort.getField().getDataType());
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

        if (keyword != null && !keyword.isEmpty()) {
            Predicate keywordPredicate = cb.or(
                    cb.like(root.get(ShowTimeField.HALL_ID.getEntityField()).as(String.class), "%" + keyword + "%"),
                    cb.like(root.get(ShowTimeField.FILM_ID.getEntityField()).as(String.class), "%" + keyword + "%"));
            predicates.add(keywordPredicate);
        }

        Predicate equalPredicate = cb.or(
                cb.equal(root.get(ShowTimeField.STATUS.getEntityField()).as(String.class), ShowTimeEnum.ShowTimeStatus.SCHEDULED.toString()),
                cb.equal(root.get(ShowTimeField.STATUS.getEntityField()).as(String.class), ShowTimeEnum.ShowTimeStatus.ONGOING.toString())
        );
        predicates.add(equalPredicate);

        if (filterBy != null) {
            for (FilterField<ShowTimeField> filter : filterBy) {
                Class<?> dataType = filter.getField().getDataType();
                Comparable filterValue = ShowTimeField.convertValue(filter.getValue().toString(), dataType);
                predicates.add(cb.equal(root.get(filter.getField().getEntityField()), filterValue));
            }
        }

        predicates.add(cb.isFalse(root.get(ShowTimeField.IS_DELETED.getEntityField())));
        cq.where(predicates.toArray(new Predicate[0]));

        // Always ensure sortBy ends with id as tie-breaker
        List<Order> orders = new ArrayList<>();
        if (sortBy != null && !sortBy.isEmpty()) {
            for (SortField<ShowTimeField> sort : sortBy) {
                String field = sort.getField().getEntityField();
                if ("DESC".equalsIgnoreCase(sort.getDirection())) {
                    orders.add(cb.desc(root.get(field)));
                } else {
                    orders.add(cb.asc(root.get(field)));
                }
            }
        }

        orders.add(cb.asc(root.get(ShowTimeField.ID.getEntityField())));
        cq.orderBy(orders);

        TypedQuery<ShowTime> query = entityManager.createQuery(cq);
        query.setMaxResults(size);

        return query.getResultList();
    }
}
