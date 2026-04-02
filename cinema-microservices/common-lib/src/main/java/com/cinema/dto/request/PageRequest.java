package com.cinema.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import org.springframework.data.domain.Pageable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageRequest<T extends Enum<T>> {

    @Min(value = 1, message = "Page number must be at least 1")
    private Integer page;

    @Min(value = 1, message = "Page size must be at least 1")
    @Max(value = 100, message = "Page size must not exceed 100")
    private Integer size;

    @Size(max = 255, message = "Keyword must not exceed 255 characters")
    private String keyword;

    private List<SortField<T>> sortBy;

    private List<FilterField<T>> filterBy;

    public int getPageOrDefault() {
        return page == null || page < 1 ? 1 : page; // 1-indexed for request
    }

    public int getSizeOrDefault() {
        return size == null ? 20 : size;
    }

    public String getNormalizedKeyword() {
        return (keyword != null && !keyword.trim().isEmpty())
                ? keyword.trim().toLowerCase()
                : null;
    }

    public Pageable toPageable() {
        return org.springframework.data.domain.PageRequest.of(
                getPageOrDefault() - 1,
                getSizeOrDefault()
        );
    }
}
