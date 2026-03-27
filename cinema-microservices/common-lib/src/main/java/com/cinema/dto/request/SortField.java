package com.cinema.dto.request;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SortField<T extends Enum<T>> {
    private T field;

    @Pattern(regexp = "^(ASC|DESC)$", message = "Direction must be ASC or DESC")
    @Builder.Default
    private String direction = "DESC";
}