package com.cinema.review_service.dto.response;

import com.cinema.review_service.entity.enums.ReviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResponse {

    private UUID id;
    private UUID filmId;
    private UUID userId;
    private String name;
    private Integer rating;
    private String title;
    private String content;
    private ReviewStatus status;
    @Builder.Default
    private List<String> mediaUrls = List.of();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
