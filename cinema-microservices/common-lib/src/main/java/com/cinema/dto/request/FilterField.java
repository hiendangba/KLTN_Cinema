package com.cinema.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lớp tổng quát cho điều kiện lọc (filter) trong truy vấn phân trang.
 * field: tên trường cần lọc (vd: "startDate", "title", "genre", ...)
 * operator: toán tử lọc (vd: "eq", "gte", "lte", "like", ...)
 * value: giá trị lọc (dạng Object, có thể là String, Number, Date, ...)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilterField<T extends Enum<T>> {
    @NotNull(message = "Filter field is required")
    private T field;
    @NotBlank(message = "Operator is required")
    @Pattern(regexp = "^(EQ|LIKE|GTE|LTE|NEQ|IN|BETWEEN)$", message = "Operator must be one of: EQ, LIKE , GTE, LTE, NEQ, IN, BETWEEN")
    private String operator;
    @NotNull(message = "Filter value is required")
    private Object value;
}
