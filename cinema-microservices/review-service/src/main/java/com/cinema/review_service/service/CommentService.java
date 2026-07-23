package com.cinema.review_service.service;

import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.review_service.dto.request.CommentCursorPageRequest;
import com.cinema.review_service.dto.request.CreateCommentRequest;
import com.cinema.review_service.dto.request.UpdateCommentRequest;
import com.cinema.review_service.dto.response.CommentResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface CommentService {

    CommentResponse createRootComment(UUID reviewId, CreateCommentRequest request, HttpServletRequest httpRequest);

    CursorPageResponse<CommentResponse> searchRootComments(UUID reviewId, CommentCursorPageRequest request);

    CommentResponse replyToComment(UUID commentId, CreateCommentRequest request, HttpServletRequest httpRequest);

    CursorPageResponse<CommentResponse> searchReplies(UUID commentId, CommentCursorPageRequest request);

    CommentResponse updateComment(UUID commentId, UpdateCommentRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse deleteComment(UUID commentId, HttpServletRequest httpRequest);
}
