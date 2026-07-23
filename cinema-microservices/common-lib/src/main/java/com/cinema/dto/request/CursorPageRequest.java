
package com.cinema.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CursorPageRequest<T extends Enum<T>> {

    private String cursor;

    @Min(value = 1, message = "Page size must be at least 1")
    @Max(value = 100, message = "Page size must not exceed 100")
    private Integer size;

    @Size(max = 255, message = "Keyword must not exceed 255 characters")
    private String keyword;

    @Valid
    private List<SortField<T>> sortBy;

    @Valid
    private List<FilterField<T>> filterBy;

    /**
     * Parses a composite cursor in the format "createdAt_id" (e.g.,
     * "2024-03-27T12:00:00.000Z_123e4567-e89b-12d3-a456-426614174000")
     * Returns a String[2] with [0]=createdAt, [1]=id, or null if cursor is empty.
     */
    public String[] getParsedCompositeCursor() {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        try {
            String cursorDecode = new String(Base64.getUrlDecoder().decode(cursor));
            return cursorDecode.split("_");
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Utility to encode a composite cursor from any number of fields.
     */
    public static String encodeCompositeCursor(Object... fields) {
        if (fields == null || fields.length == 0)
            return null;

        String joined = Arrays.stream(fields)
                .map(f -> f != null ? f.toString() : "")
                .collect(Collectors.joining("_"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(joined.getBytes(StandardCharsets.UTF_8));
    }

    // Dùng chung cho mọi service
    public String getNormalizedKeyword() {
        return (keyword != null && !keyword.trim().isEmpty())
                ? keyword.trim().toLowerCase()
                : null;
    }

    public int getSizeOrDefault() {
        return size == null ? 20 : size;
    }
}
