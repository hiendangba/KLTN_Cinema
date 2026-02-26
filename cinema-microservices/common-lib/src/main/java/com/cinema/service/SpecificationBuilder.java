package com.cinema.service;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder class để tạo Specification động cho filter và search
 * 
 * Sử dụng pattern Builder để enable chaining và tạo flexible queries
 * 
 * Ví dụ sử dụng:
 * 
 * <pre>
 * Specification<User> spec = new SpecificationBuilder<User>()
 *         .addCriteria("status", "ACTIVE")
 *         .addLikeCriteria("email", "example")
 *         .addGreaterThanCriteria("createdDate", startDate)
 *         .build();
 * 
 * List<User> users = userRepository.findAll(spec);
 * </pre>
 * 
 * @param <T> Entity type để apply Specification
 */
public class SpecificationBuilder<T> {
    private final List<FilterCriteria> criteria = new ArrayList<>();

    /**
     * Thêm criteria EQUAL (dùng cho exact match)
     * 
     * @param key   field name
     * @param value giá trị cần match
     * @return this for chaining
     */
    public SpecificationBuilder<T> addCriteria(String key, Object value) {
        if (value != null && !value.toString().isEmpty()) {
            criteria.add(new FilterCriteria(key, value, FilterCriteria.OperationType.EQUAL));
        }
        return this;
    }

    /**
     * Thêm criteria LIKE (dùng cho string search)
     * 
     * @param key   field name
     * @param value giá trị cần search
     * @return this for chaining
     */
    public SpecificationBuilder<T> addLikeCriteria(String key, String value) {
        if (value != null && !value.isEmpty()) {
            criteria.add(new FilterCriteria(key, "%" + value + "%", FilterCriteria.OperationType.LIKE));
        }
        return this;
    }

    /**
     * Thêm criteria LIKE từ đầu (dùng cho search prefix)
     * 
     * @param key   field name
     * @param value giá trị prefix
     * @return this for chaining
     */
    public SpecificationBuilder<T> addLikeStartsCriteria(String key, String value) {
        if (value != null && !value.isEmpty()) {
            criteria.add(new FilterCriteria(key, value + "%", FilterCriteria.OperationType.LIKE));
        }
        return this;
    }

    /**
     * Thêm criteria LIKE từ cuối
     * 
     * @param key   field name
     * @param value giá trị suffix
     * @return this for chaining
     */
    
    public SpecificationBuilder<T> addLikeEndsCriteria(String key, String value) {
        if (value != null && !value.isEmpty()) {
            criteria.add(new FilterCriteria(key, "%" + value, FilterCriteria.OperationType.LIKE));
        }
        return this;
    }

    /**
     * Thêm criteria GREATER_THAN (dùng cho so sánh >)
     * 
     * @param key   field name
     * @param value giá trị để so sánh
     * @return this for chaining
     */
    public SpecificationBuilder<T> addGreaterThanCriteria(String key, Comparable<?> value) {
        if (value != null) {
            criteria.add(new FilterCriteria(key, value, FilterCriteria.OperationType.GREATER_THAN));
        }
        return this;
    }

    /**
     * Thêm criteria LESS_THAN (dùng cho so sánh <)
     * 
     * @param key   field name
     * @param value giá trị để so sánh
     * @return this for chaining
     */
    public SpecificationBuilder<T> addLessThanCriteria(String key, Comparable<?> value) {
        if (value != null) {
            criteria.add(new FilterCriteria(key, value, FilterCriteria.OperationType.LESS_THAN));
        }
        return this;
    }

    /**
     * Thêm criteria GREATER_THAN_OR_EQUAL (dùng cho so sánh >=)
     * 
     * @param key   field name
     * @param value giá trị để so sánh
     * @return this for chaining
     */
    public SpecificationBuilder<T> addGreaterThanOrEqualCriteria(String key, Comparable<?> value) {
        if (value != null) {
            criteria.add(new FilterCriteria(key, value, FilterCriteria.OperationType.GREATER_THAN_OR_EQUAL));
        }
        return this;
    }

    /**
     * Thêm criteria LESS_THAN_OR_EQUAL (dùng cho so sánh <=)
     * 
     * @param key   field name
     * @param value giá trị để so sánh
     * @return this for chaining
     */
    public SpecificationBuilder<T> addLessThanOrEqualCriteria(String key, Comparable<?> value) {
        if (value != null) {
            criteria.add(new FilterCriteria(key, value, FilterCriteria.OperationType.LESS_THAN_OR_EQUAL));
        }
        return this;
    }

    /**
     * Thêm criteria BETWEEN (dùng cho range search)
     * 
     * @param key  field name
     * @param from giá trị từ
     * @param to   giá trị đến
     * @return this for chaining
     */
    public SpecificationBuilder<T> addBetweenCriteria(String key, Comparable<?> from, Comparable<?> to) {
        if (from != null) {
            criteria.add(new FilterCriteria(key, from, FilterCriteria.OperationType.GREATER_THAN_OR_EQUAL));
        }
        if (to != null) {
            criteria.add(new FilterCriteria(key, to, FilterCriteria.OperationType.LESS_THAN_OR_EQUAL));
        }
        return this;
    }

    /**
     * Xóa tất cả criteria (reset builder)
     * 
     * @return this for chaining
     */
    public SpecificationBuilder<T> reset() {
        criteria.clear();
        return this;
    }

    /**
     * Build Specification từ các criteria đã thêm
     * Tất cả criteria được kết hợp bằng AND
     * 
     * @return Specification<T> hoặc null nếu không có criteria
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Specification<T> build() {
        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder builder) -> {
            if (criteria.isEmpty()) {
                return null;
            }

            List<Predicate> predicates = new ArrayList<>();

            for (FilterCriteria c : criteria) {
                switch (c.operationType) {
                    case EQUAL:
                        predicates.add(builder.equal(root.get(c.key), c.value));
                        break;
                    case LIKE:
                        predicates.add(builder.like(
                                builder.lower(root.get(c.key).as(String.class)),
                                c.value.toString().toLowerCase()));
                        break;
                    case GREATER_THAN:
                        predicates.add(builder.greaterThan(
                                root.get(c.key).as((Class) c.value.getClass()),
                                (Comparable) c.value));
                        break;
                    case LESS_THAN:
                        predicates.add(builder.lessThan(
                                root.get(c.key).as((Class) c.value.getClass()),
                                (Comparable) c.value));
                        break;
                    case GREATER_THAN_OR_EQUAL:
                        predicates.add(builder.greaterThanOrEqualTo(
                                root.get(c.key).as((Class) c.value.getClass()),
                                (Comparable) c.value));
                        break;
                    case LESS_THAN_OR_EQUAL:
                        predicates.add(builder.lessThanOrEqualTo(
                                root.get(c.key).as((Class) c.value.getClass()),
                                (Comparable) c.value));
                        break;
                }
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Inner class để lưu trữ filter criteria
     */
    private static class FilterCriteria {
        String key;
        Object value;
        OperationType operationType;

        FilterCriteria(String key, Object value, OperationType operationType) {
            this.key = key;
            this.value = value;
            this.operationType = operationType;
        }

        enum OperationType {
            EQUAL, LIKE, GREATER_THAN, LESS_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN_OR_EQUAL
        }
    }
}
