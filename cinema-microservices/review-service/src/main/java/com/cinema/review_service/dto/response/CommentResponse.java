package com.cinema.review_service.dto.response;

import com.cinema.review_service.entity.enums.CommentStatus;
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
public class CommentResponse {

    private UUID id;
    private UUID reviewId;
    private UUID parentCommentId;
    private UUID userId;
    private String content;
    private CommentStatus status;
    @Builder.Default
    private List<String> mediaUrls = List.of();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
