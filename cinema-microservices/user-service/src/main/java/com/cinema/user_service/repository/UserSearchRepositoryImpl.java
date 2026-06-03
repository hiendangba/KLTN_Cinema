package com.cinema.user_service.repository;

import com.cinema.Enum.UserEnum;
import com.cinema.text.SearchTextUtils;
import com.cinema.user_service.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserSearchRepositoryImpl implements UserSearchRepository {

    @PersistenceContext
    private final EntityManager entityManager;

    @Override
    public Page<User> searchByRoleAndKeyword(UserEnum.UserRole role, String keyword, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> cq = cb.createQuery(User.class);
        Root<User> root = cq.from(User.class);

        List<Predicate> predicates = buildPredicates(cb, root, role, keyword);
        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(buildOrders(cb, root));

        TypedQuery<User> query = entityManager.createQuery(cq);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<User> content = query.getResultList();
        return new PageImpl<>(content, pageable, countByRoleAndKeyword(role, keyword));
    }

    @Override
    public long countByRoleAndKeyword(UserEnum.UserRole role, String keyword) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<User> root = countQuery.from(User.class);

        List<Predicate> predicates = buildPredicates(cb, root, role, keyword);
        countQuery.select(cb.count(root));
        countQuery.where(predicates.toArray(new Predicate[0]));
        return entityManager.createQuery(countQuery).getSingleResult();
    }

    private List<Predicate> buildPredicates(
            CriteriaBuilder cb,
            Root<User> root,
            UserEnum.UserRole role,
            String keyword) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("role"), role));
        predicates.add(cb.isFalse(root.get("isDeleted")));

        Predicate keywordPredicate = buildKeywordPredicate(cb, root, keyword);
        if (keywordPredicate != null) {
            predicates.add(keywordPredicate);
        }

        return predicates;
    }

    private List<Order> buildOrders(CriteriaBuilder cb, Root<User> root) {
        return List.of(
                cb.desc(root.get("timeCreated")),
                cb.asc(root.get("id")));
    }

    private Predicate buildKeywordPredicate(CriteriaBuilder cb, Root<User> root, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(SearchTextUtils.accentInsensitiveLike(cb, root.get("id"), keyword));
        predicates.add(SearchTextUtils.accentInsensitiveLike(cb, root.get("name"), keyword));
        predicates.add(SearchTextUtils.accentInsensitiveLike(cb, root.get("email"), keyword));
        predicates.add(SearchTextUtils.accentInsensitiveLike(cb, root.get("phone"), keyword));
        predicates.add(SearchTextUtils.accentInsensitiveLike(cb, root.get("bankCode"), keyword));
        predicates.add(SearchTextUtils.accentInsensitiveLike(cb, root.get("accountNumber"), keyword));
        predicates.add(SearchTextUtils.accentInsensitiveLike(cb, root.get("accountName"), keyword));

        try {
            UUID keywordUuid = UUID.fromString(keyword.trim());
            predicates.add(cb.equal(root.get("id"), keywordUuid));
        } catch (IllegalArgumentException ignored) {
        }

        return cb.or(predicates.toArray(new Predicate[0]));
    }
}
