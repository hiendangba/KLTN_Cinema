package com.cinema.text;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.Locale;

public final class SearchTextUtils {

    private static final String VIETNAMESE_ACCENTED_LOWER = "àáạảãăằắặẳẵâầấậẩẫđèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹ";
    private static final String VIETNAMESE_ASCII = "aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy";

    private SearchTextUtils() {
    }

    public static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return normalized.replace('đ', 'd');
    }

    public static boolean containsIgnoreCase(String text, String keyword) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(keyword)) {
            return false;
        }
        return normalize(text).contains(normalize(keyword));
    }

    public static Predicate accentInsensitiveLike(
            CriteriaBuilder cb,
            Expression<?> expression,
            String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return cb.like(accentInsensitiveExpression(cb, expression), "%" + normalize(keyword) + "%");
    }

    public static Expression<String> accentInsensitiveExpression(
            CriteriaBuilder cb,
            Expression<?> expression) {
        return cb.function(
                "translate",
                String.class,
                cb.lower(cb.function("concat", String.class, cb.literal(""), expression)),
                cb.literal(VIETNAMESE_ACCENTED_LOWER),
                cb.literal(VIETNAMESE_ASCII));
    }
}
