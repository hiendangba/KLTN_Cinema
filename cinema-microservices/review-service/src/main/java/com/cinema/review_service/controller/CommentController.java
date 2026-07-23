package com.cinema.review_service.controller;

import com.cinema.Enum.SuccessMessage;
import com.cinema.controller.BaseController;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.review_service.dto.request.CommentCursorPageRequest;
import com.cinema.review_service.dto.request.CreateCommentRequest;
import com.cinema.review_service.dto.request.UpdateCommentRequest;
import com.cinema.review_service.dto.response.CommentResponse;
import com.cinema.review_service.service.CommentService;
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
public class CommentController extends BaseController {

    private final CommentService commentService;

    @PostMapping("/reviews/{reviewId}/comments")
    public ResponseEntity<APIResponse<CommentResponse>> createRootComment(
            @PathVariable UUID reviewId,
            @Valid @RequestBody CreateCommentRequest request,
            HttpServletRequest httpRequest) {
        CommentResponse response = commentService.createRootComment(reviewId, request, httpRequest);
        return created(SuccessMessage.CREATED, response);
    }

    @PostMapping("/reviews/{reviewId}/comments/search")
    public ResponseEntity<APIResponse<CursorPageResponse<CommentResponse>>> searchRootComments(
            @PathVariable UUID reviewId,
            @Valid @RequestBody CommentCursorPageRequest request) {
        CursorPageResponse<CommentResponse> response = commentService.searchRootComments(reviewId, request);
        return ok(SuccessMessage.SEARCHED, response);
    }

    @PostMapping("/comments/{commentId}/reply")
    public ResponseEntity<APIResponse<CommentResponse>> replyToComment(
            @PathVariable UUID commentId,
            @Valid @RequestBody CreateCommentRequest request,
            HttpServletRequest httpRequest) {
        CommentResponse response = commentService.replyToComment(commentId, request, httpRequest);
        return created(SuccessMessage.CREATED, response);
    }

    @PostMapping("/comments/{commentId}/replies/search")
    public ResponseEntity<APIResponse<CursorPageResponse<CommentResponse>>> searchReplies(
            @PathVariable UUID commentId,
            @Valid @RequestBody CommentCursorPageRequest request) {
        CursorPageResponse<CommentResponse> response = commentService.searchReplies(commentId, request);
        return ok(SuccessMessage.SEARCHED, response);
    }

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<APIResponse<CommentResponse>> updateComment(
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdateCommentRequest request,
            HttpServletRequest httpRequest) {
        CommentResponse response = commentService.updateComment(commentId, request, httpRequest);
        return ok(SuccessMessage.UPDATED, response);
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> deleteComment(
            @PathVariable UUID commentId,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = commentService.deleteComment(commentId, httpRequest);
        return ok(SuccessMessage.DELETED, response);
    }
}
