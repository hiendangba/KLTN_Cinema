package com.cinema.review_service.service.impl;

import com.cinema.http.HeaderNames;
import com.cinema.review_service.dto.request.CreateReviewRequest;
import com.cinema.review_service.dto.request.ReviewCursorPageRequest;
import com.cinema.review_service.dto.response.ReviewResponse;
import com.cinema.review_service.entity.Review;
import com.cinema.review_service.entity.enums.ReviewStatus;
import com.cinema.review_service.grpc.BookingGrpcClient;
import com.cinema.review_service.grpc.UserGrpcClient;
import com.cinema.review_service.mapper.ReviewMapper;
import com.cinema.review_service.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private BookingGrpcClient bookingGrpcClient;

    @Mock
    private UserGrpcClient userGrpcClient;

    @Mock
    private ReviewMapper reviewMapper;

    private ReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReviewServiceImpl(reviewRepository, bookingGrpcClient, userGrpcClient, reviewMapper);
    }

    @Test
    void createReview_shouldAttachUserNameToResponse() {
        UUID userId = UUID.randomUUID();
        UUID filmId = UUID.randomUUID();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderNames.X_USER_ID, userId.toString());

        CreateReviewRequest body = CreateReviewRequest.builder()
                .rating(5)
                .title("Phim cực hay")
                .content("Rất tuyệt vời")
                .mediaUrls(List.of())
                .build();

        Review review = Review.builder()
                .medias(new ArrayList<>())
                .build();
        Review savedReview = Review.builder()
                .id(UUID.randomUUID())
                .filmId(filmId)
                .userId(userId)
                .rating(5)
                .title("Phim cực hay")
                .content("Rất tuyệt vời")
                .status(ReviewStatus.ACTIVE)
                .medias(new ArrayList<>())
                .build();

        when(reviewRepository.findByUserIdAndFilmIdAndIsDeletedFalse(userId, filmId)).thenReturn(Optional.empty());
        when(bookingGrpcClient.isUserEligibleForReview(userId, filmId)).thenReturn(true);
        when(reviewMapper.toEntity(body)).thenReturn(review);
        when(reviewRepository.save(review)).thenReturn(savedReview);
        when(reviewMapper.toResponse(savedReview)).thenAnswer(invocation -> toResponse(invocation.getArgument(0)));
        when(userGrpcClient.getUserNameById(userId)).thenReturn("Nguyễn Văn A");

        ReviewResponse response = service.createReview(filmId, body, request);

        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.getFilmId()).isEqualTo(filmId);
        assertThat(response.getRating()).isEqualTo(5);
    }

    @Test
    void searchReviews_shouldAttachUserNameToEachResponse() {
        UUID userId = UUID.randomUUID();
        UUID filmId = UUID.randomUUID();

        Review review = Review.builder()
                .id(UUID.randomUUID())
                .filmId(filmId)
                .userId(userId)
                .rating(4)
                .title("Ổn")
                .content("Xem được")
                .status(ReviewStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .medias(new ArrayList<>())
                .build();

        ReviewCursorPageRequest request = new ReviewCursorPageRequest();
        request.setSize(10);

        when(reviewRepository.searchByFilmIdAndCursor(
                eq(filmId),
                isNull(),
                isNull(),
                isNull(),
                any(PageRequest.class)))
                .thenReturn(List.of(review));
        when(reviewMapper.toResponse(review)).thenAnswer(invocation -> toResponse(invocation.getArgument(0)));
        when(userGrpcClient.getUserNameById(userId)).thenReturn("Trần Thị B");

        var page = service.searchReviews(filmId, request);

        assertThat(page.getData()).hasSize(1);
        assertThat(page.getData().get(0).getUserId()).isEqualTo(userId);
        assertThat(page.getData().get(0).getName()).isEqualTo("Trần Thị B");
    }

    private ReviewResponse toResponse(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .filmId(review.getFilmId())
                .userId(review.getUserId())
                .name(null)
                .rating(review.getRating())
                .title(review.getTitle())
                .content(review.getContent())
                .status(review.getStatus())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
