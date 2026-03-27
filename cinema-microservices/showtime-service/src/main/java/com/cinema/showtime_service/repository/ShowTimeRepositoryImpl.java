package com.cinema.showtime_service.repository;

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
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class ShowTimeRepositoryImpl {
    @PersistenceContext
    private EntityManager entityManager;

    public List<ShowTime> searchWithCursorAndSortAndFilter(
            UUID cursor,
            String keyword,
            int size,
            List<SortField<ShowTimeField>> sortBy,
            List<FilterField<ShowTimeField>> filterBy) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<ShowTime> cq = cb.createQuery(ShowTime.class);
        Root<ShowTime> root = cq.from(ShowTime.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(root.get("isDeleted")));

        if (cursor != null) {
            predicates.add(cb.greaterThan(root.get("id"), cursor));
        }

        if (keyword != null && !keyword.isEmpty()) {
            Predicate keywordPredicate = cb.or(
                    cb.like(cb.lower(root.get(ShowTimeField.ID.getEntityField())), "%" + keyword.toLowerCase() + "%"),
                    cb.like(cb.lower(root.get(ShowTimeField.STATUS.getEntityField())),
                            "%" + keyword.toLowerCase() + "%"),
                    cb.like(cb.lower(root.get(ShowTimeField.HALL_ID.getEntityField())),
                            "%" + keyword.toLowerCase() + "%"),
                    cb.like(cb.lower(root.get(ShowTimeField.FILM_ID.getEntityField())),
                            "%" + keyword.toLowerCase() + "%"));
            predicates.add(keywordPredicate);
        }

        if (filterBy != null) {
            for (FilterField<ShowTimeField> filter : filterBy) {
                predicates.add(cb.equal(root.get(filter.getField().getEntityField()), filter.getValue()));
            }
        }

        cq.where(predicates.toArray(new Predicate[0]));

        // Sort động
        if (sortBy != null && !sortBy.isEmpty()) {
            List<Order> orders = new ArrayList<>();
            for (SortField<ShowTimeField> sort : sortBy) {
                if ("DESC".equalsIgnoreCase(sort.getDirection())) {
                    orders.add(cb.desc(root.get(sort.getField().getEntityField())));
                } else {
                    orders.add(cb.asc(root.get(sort.getField().getEntityField())));
                }
            }
            cq.orderBy(orders);
        } else {
            cq.orderBy(cb.asc(root.get(ShowTimeField.ID.getEntityField())));
        }

        TypedQuery<ShowTime> query = entityManager.createQuery(cq);
        query.setMaxResults(size + 1);

        return query.getResultList();
    }
}
