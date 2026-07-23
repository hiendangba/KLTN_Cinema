package com.cinema.review_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCommentRequest {

    @NotBlank(message = "Content is required")
    private String content;

    @NotNull(message = "Media URLs are required")
    @Size(max = 5, message = "Media count must not exceed 5")
    private List<String> mediaUrls;
}
