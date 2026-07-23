package com.cinema.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SortField<T extends Enum<T>> {
    @NotNull(message = "Sort field is required")
    private T field;

    @NotBlank(message = "Direction is required")
    @Pattern(regexp = "^(ASC|DESC)$", message = "Direction must be ASC or DESC")
    @Builder.Default
    private String direction = "DESC";
}
