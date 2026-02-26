package com.cinema.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BasePageRequest {
    @JsonProperty("page")
    @Builder.Default
    private Integer page = 0;

    @JsonProperty("size")
    @Builder.Default
    private Integer size = 10;

    @JsonProperty("sort_by")
    private String sortBy;

    @JsonProperty("sort_direction")
    @Builder.Default
    private String sortDirection = "ASC";

    public void validate() {
        if (this.page == null || this.page < 0) {
            this.page = 0;
        }
        if (this.size == null || this.size <= 0 || this.size > 100) {
            this.size = 10;
        }
        if (this.sortDirection == null) {
            this.sortDirection = "ASC";
        }
    }
}
