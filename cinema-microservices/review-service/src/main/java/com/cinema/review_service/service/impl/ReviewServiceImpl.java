package com.cinema.review_service.service.impl;

import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.RequestAuthUtils;
import com.cinema.review_service.dto.request.CreateReviewRequest;
import com.cinema.review_service.dto.request.ReviewCursorPageRequest;
import com.cinema.review_service.dto.request.UpdateReviewRequest;
import com.cinema.review_service.dto.response.ReviewResponse;
import com.cinema.review_service.entity.Review;
import com.cinema.review_service.entity.ReviewMedia;
import com.cinema.review_service.entity.enums.ReviewStatus;
import com.cinema.review_service.grpc.BookingGrpcClient;
import com.cinema.review_service.mapper.ReviewMapper;
import com.cinema.review_service.repository.ReviewRepository;
import com.cinema.review_service.service.ReviewService;
import com.cinema.review_service.util.MediaUrlUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingGrpcClient bookingGrpcClient;
    private final ReviewMapper reviewMapper;

    @Override
    public ReviewResponse createReview(UUID filmId, CreateReviewRequest request, HttpServletRequest httpRequest) {
        UUID userId = RequestAuthUtils.requireUserId(httpRequest);
        validateRating(request.getRating());
        String content = normalizeRequiredText(request.getContent());
        String title = normalizeOptionalText(request.getTitle());
        List<MediaUrlUtils.MediaDescriptor> mediaDescriptors = normalizeMediaDescriptors(request.getMediaUrls());

        if (reviewRepository.findByUserIdAndFilmIdAndIsDeletedFalse(userId, filmId).isPresent()) {
            throw new BusinessException(ErrorCode.ID_EXISTED);
        }
        if (!bookingGrpcClient.isUserEligibleForReview(userId, filmId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Review review = reviewMapper.toEntity(request);
        review.setFilmId(filmId);
        review.setUserId(userId);
        review.setTitle(title);
        review.setContent(content);
        review.setStatus(ReviewStatus.ACTIVE);
        review.setDeletedAt(null);
        applyReviewMedias(review, mediaDescriptors);
        return reviewMapper.toResponse(reviewRepository.save(review));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<ReviewResponse> searchReviews(UUID filmId, ReviewCursorPageRequest request) {
        CursorAnchor cursorAnchor = parseCursor(request.getCursor());
        int size = request.getLimitedSize();

        List<Review> reviews = reviewRepository.searchByFilmIdAndCursor(
                filmId,
                cursorAnchor.createdAt(),
                cursorAnchor.id(),
                toLikePattern(request.getNormalizedKeyword()),
                PageRequest.of(0, size + 1));

        return toCursorPageResponse(reviews, size, reviewMapper::toResponse);
    }

    @Override
    public ReviewResponse updateReview(UUID reviewId, UpdateReviewRequest request, HttpServletRequest httpRequest) {
        UUID userId = RequestAuthUtils.requireUserId(httpRequest);
        validateRating(request.getRating());
        String content = normalizeRequiredText(request.getContent());
        String title = normalizeOptionalText(request.getTitle());
        List<MediaUrlUtils.MediaDescriptor> mediaDescriptors = normalizeMediaDescriptors(request.getMediaUrls());

        Review review = loadReviewOrThrow(reviewId);
        ensureReviewOwner(review, userId);

        reviewMapper.updateEntityFromRequest(review, request);
        review.setTitle(title);
        review.setContent(content);
        review.setStatus(ReviewStatus.ACTIVE);
        review.setDeletedAt(null);
        applyReviewMedias(review, mediaDescriptors);
        return reviewMapper.toResponse(reviewRepository.save(review));
    }

    @Override
    public ActionMessageResponse deleteReview(UUID reviewId, HttpServletRequest httpRequest) {
        UUID userId = RequestAuthUtils.requireUserId(httpRequest);
        Review review = loadReviewOrThrow(reviewId);
        ensureReviewOwner(review, userId);

        review.setIsDeleted(true);
        review.setDeletedAt(LocalDateTime.now());
        review.setStatus(ReviewStatus.HIDDEN);
        reviewRepository.save(review);
        return ActionMessageResponse.builder()
                .message("Review deleted successfully")
                .build();
    }

    private Review loadReviewOrThrow(UUID reviewId) {
        return reviewRepository.findByIdAndIsDeletedFalse(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void ensureReviewOwner(Review review, UUID userId) {
        if (review == null || review.getUserId() == null || !review.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateRating(Integer rating) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private String normalizeRequiredText(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<MediaUrlUtils.MediaDescriptor> normalizeMediaDescriptors(List<String> mediaUrls) {
        List<MediaUrlUtils.MediaDescriptor> descriptors = MediaUrlUtils.normalize(mediaUrls);
        if (descriptors.size() > 5) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return descriptors;
    }

    private void applyReviewMedias(Review review, List<MediaUrlUtils.MediaDescriptor> mediaDescriptors) {
        review.getMedias().clear();
        for (MediaUrlUtils.MediaDescriptor descriptor : mediaDescriptors) {
            review.getMedias().add(ReviewMedia.builder()
                    .review(review)
                    .mediaUrl(descriptor.mediaUrl())
                    .mediaType(descriptor.mediaType())
                    .build());
        }
    }

    private CursorAnchor parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new CursorAnchor(null, null);
        }

        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }

        String[] parsedCursor = decoded.split("_");
        if (parsedCursor.length != 2) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }

        try {
            return new CursorAnchor(LocalDateTime.parse(parsedCursor[0]), UUID.fromString(parsedCursor[1]));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }
    }

    private <T> CursorPageResponse<T> toCursorPageResponse(List<Review> items, int limit, java.util.function.Function<Review, T> mapper) {
        boolean hasNext = items.size() > limit;
        List<Review> content = hasNext ? new ArrayList<>(items.subList(0, limit)) : new ArrayList<>(items);
        List<T> data = content.stream().map(mapper).toList();
        String nextCursor = hasNext && !content.isEmpty()
                ? com.cinema.dto.request.CursorPageRequest.encodeCompositeCursor(
                content.get(content.size() - 1).getCreatedAt(),
                content.get(content.size() - 1).getId())
                : null;

        return CursorPageResponse.<T>builder()
                .data(data)
                .nextCursor(nextCursor)
                .prevCursor(null)
                .hasNext(hasNext)
                .size(data.size())
                .build();
    }

    private String toLikePattern(String keyword) {
        return keyword == null ? null : "%" + keyword + "%";
    }

    private record CursorAnchor(LocalDateTime createdAt, UUID id) {
    }
}
