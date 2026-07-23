package com.cinema.review_service.mapper;

import com.cinema.review_service.dto.request.CreateCommentRequest;
import com.cinema.review_service.dto.request.UpdateCommentRequest;
import com.cinema.review_service.dto.response.CommentResponse;
import com.cinema.review_service.entity.Comment;
import com.cinema.review_service.entity.CommentMedia;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import java.util.Comparator;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CommentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "reviewId", ignore = true)
    @Mapping(target = "parentCommentId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "medias", ignore = true)
    Comment toEntity(CreateCommentRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "reviewId", ignore = true)
    @Mapping(target = "parentCommentId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "medias", ignore = true)
    void updateEntityFromRequest(@MappingTarget Comment comment, UpdateCommentRequest request);

    default CommentResponse toResponse(Comment comment) {
        if (comment == null) {
            return null;
        }

        return CommentResponse.builder()
                .id(comment.getId())
                .reviewId(comment.getReviewId())
                .parentCommentId(comment.getParentCommentId())
                .userId(comment.getUserId())
                .content(comment.getContent())
                .status(comment.getStatus())
                .mediaUrls(mapMediaUrls(comment.getMedias()))
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

    default List<String> mapMediaUrls(List<CommentMedia> medias) {
        if (medias == null || medias.isEmpty()) {
            return List.of();
        }

        return medias.stream()
                .sorted(Comparator.comparing(
                        CommentMedia::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(CommentMedia::getMediaUrl)
                .toList();
    }
}
