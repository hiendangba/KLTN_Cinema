package com.cinema.review_service.controller;

import com.cinema.Enum.SuccessMessage;
import com.cinema.controller.BaseController;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.review_service.dto.request.CreateReviewRequest;
import com.cinema.review_service.dto.request.ReviewCursorPageRequest;
import com.cinema.review_service.dto.request.UpdateReviewRequest;
import com.cinema.review_service.dto.response.ReviewResponse;
import com.cinema.review_service.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController extends BaseController {

    private final ReviewService reviewService;

    @PostMapping("/films/{filmId}/reviews")
    public ResponseEntity<APIResponse<ReviewResponse>> createReview(
            @PathVariable UUID filmId,
            @Valid @RequestBody CreateReviewRequest request,
            HttpServletRequest httpRequest) {
        ReviewResponse response = reviewService.createReview(filmId, request, httpRequest);
        return created(SuccessMessage.CREATED, response);
    }

    @PostMapping("/films/{filmId}/reviews/search")
    public ResponseEntity<APIResponse<CursorPageResponse<ReviewResponse>>> searchReviews(
            @PathVariable UUID filmId,
            @Valid @RequestBody ReviewCursorPageRequest request) {
        CursorPageResponse<ReviewResponse> response = reviewService.searchReviews(filmId, request);
        return ok(SuccessMessage.SEARCHED, response);
    }

    @PutMapping("/reviews/{reviewId}")
    public ResponseEntity<APIResponse<ReviewResponse>> updateReview(
            @PathVariable UUID reviewId,
            @Valid @RequestBody UpdateReviewRequest request,
            HttpServletRequest httpRequest) {
        ReviewResponse response = reviewService.updateReview(reviewId, request, httpRequest);
        return ok(SuccessMessage.UPDATED, response);
    }

    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> deleteReview(
            @PathVariable UUID reviewId,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = reviewService.deleteReview(reviewId, httpRequest);
        return ok(SuccessMessage.DELETED, response);
    }
}
