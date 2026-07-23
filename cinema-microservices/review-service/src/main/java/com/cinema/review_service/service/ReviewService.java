package com.cinema.review_service.service;

import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.review_service.dto.request.CreateReviewRequest;
import com.cinema.review_service.dto.request.ReviewCursorPageRequest;
import com.cinema.review_service.dto.request.UpdateReviewRequest;
import com.cinema.review_service.dto.response.ReviewResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface ReviewService {

    ReviewResponse createReview(UUID filmId, CreateReviewRequest request, HttpServletRequest httpRequest);

    CursorPageResponse<ReviewResponse> searchReviews(UUID filmId, ReviewCursorPageRequest request);

    ReviewResponse updateReview(UUID reviewId, UpdateReviewRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse deleteReview(UUID reviewId, HttpServletRequest httpRequest);
}
