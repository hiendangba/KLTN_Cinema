package com.cinema.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CursorPageRequest<T extends Enum<T>> {

    private String cursor;

    @Min(value = 1, message = "Page size must be at least 1")
    @Max(value = 100, message = "Page size must not exceed 100")
    @Builder.Default
    private int size = 10;

    @Size(max = 255, message = "Keyword must not exceed 255 characters")
    private String keyword;

    @Pattern(regexp = "^(ASC|DESC)$", message = "Direction must be ASC or DESC")
    @Builder.Default
    private String direction = "DESC";

    private T sortBy;

    public UUID getParsedCursor() {
        if (cursor == null || cursor.isEmpty()) return null;
        try {
            return UUID.fromString(cursor);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e);
        }
    }

    // Dùng chung cho mọi service
    public String getNormalizedKeyword() {
        return (keyword != null && !keyword.trim().isEmpty())
                ? keyword.trim().toLowerCase()
                : null;
    }
}