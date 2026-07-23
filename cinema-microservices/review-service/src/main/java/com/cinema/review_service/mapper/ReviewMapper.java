package com.cinema.review_service.mapper;

import com.cinema.review_service.dto.request.CreateReviewRequest;
import com.cinema.review_service.dto.request.UpdateReviewRequest;
import com.cinema.review_service.dto.response.ReviewResponse;
import com.cinema.review_service.entity.Review;
import com.cinema.review_service.entity.ReviewMedia;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import java.util.Comparator;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ReviewMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "filmId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "medias", ignore = true)
    Review toEntity(CreateReviewRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "filmId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "medias", ignore = true)
    void updateEntityFromRequest(@MappingTarget Review review, UpdateReviewRequest request);

    default ReviewResponse toResponse(Review review) {
        if (review == null) {
            return null;
        }

        return ReviewResponse.builder()
                .id(review.getId())
                .filmId(review.getFilmId())
                .userId(review.getUserId())
                .rating(review.getRating())
                .title(review.getTitle())
                .content(review.getContent())
                .status(review.getStatus())
                .mediaUrls(mapMediaUrls(review.getMedias()))
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    default List<String> mapMediaUrls(List<ReviewMedia> medias) {
        if (medias == null || medias.isEmpty()) {
            return List.of();
        }

        return medias.stream()
                .sorted(Comparator.comparing(
                        ReviewMedia::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(ReviewMedia::getMediaUrl)
                .toList();
    }
}
