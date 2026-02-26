package com.cinema.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BasePageResponse<T> {
    @JsonProperty("data")
    private List<T> data;

    @JsonProperty("pagination")
    private PageMetadata pagination;

    public BasePageResponse(List<T> data, Integer currentPage, Integer pageSize, Long totalElements) {
        this.data = data;
        this.pagination = PageMetadata.of(currentPage, pageSize, totalElements);
    }
}
