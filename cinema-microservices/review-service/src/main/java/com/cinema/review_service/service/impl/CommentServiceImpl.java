package com.cinema.review_service.service.impl;

import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.RequestAuthUtils;
import com.cinema.review_service.dto.request.CommentCursorPageRequest;
import com.cinema.review_service.dto.request.CreateCommentRequest;
import com.cinema.review_service.dto.request.UpdateCommentRequest;
import com.cinema.review_service.dto.response.CommentResponse;
import com.cinema.review_service.entity.Comment;
import com.cinema.review_service.entity.CommentMedia;
import com.cinema.review_service.entity.enums.CommentStatus;
import com.cinema.review_service.mapper.CommentMapper;
import com.cinema.review_service.repository.CommentRepository;
import com.cinema.review_service.repository.ReviewRepository;
import com.cinema.review_service.service.CommentService;
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
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final ReviewRepository reviewRepository;
    private final CommentMapper commentMapper;

    @Override
    public CommentResponse createRootComment(UUID reviewId, CreateCommentRequest request, HttpServletRequest httpRequest) {
        UUID userId = RequestAuthUtils.requireUserId(httpRequest);
        String content = normalizeRequiredText(request.getContent());
        List<MediaUrlUtils.MediaDescriptor> mediaDescriptors = normalizeMediaDescriptors(request.getMediaUrls());

        ensureReviewExists(reviewId);

        Comment comment = commentMapper.toEntity(request);
        comment.setReviewId(reviewId);
        comment.setParentCommentId(null);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setStatus(CommentStatus.ACTIVE);
        comment.setDeletedAt(null);
        applyCommentMedias(comment, mediaDescriptors);
        return commentMapper.toResponse(commentRepository.save(comment));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<CommentResponse> searchRootComments(UUID reviewId, CommentCursorPageRequest request) {
        ensureReviewExists(reviewId);
        CursorAnchor cursorAnchor = parseCursor(request.getCursor());
        int size = request.getLimitedSize();

        List<Comment> comments = commentRepository.searchRootCommentsByReviewAndCursor(
                reviewId,
                cursorAnchor.createdAt(),
                cursorAnchor.id(),
                request.getNormalizedKeyword(),
                PageRequest.of(0, size + 1));

        return toCursorPageResponse(comments, size, commentMapper::toResponse);
    }

    @Override
    public CommentResponse replyToComment(UUID commentId, CreateCommentRequest request, HttpServletRequest httpRequest) {
        UUID userId = RequestAuthUtils.requireUserId(httpRequest);
        String content = normalizeRequiredText(request.getContent());
        List<MediaUrlUtils.MediaDescriptor> mediaDescriptors = normalizeMediaDescriptors(request.getMediaUrls());

        Comment parent = loadCommentOrThrow(commentId);
        Comment reply = commentMapper.toEntity(request);
        reply.setReviewId(parent.getReviewId());
        reply.setParentCommentId(parent.getId());
        reply.setUserId(userId);
        reply.setContent(content);
        reply.setStatus(CommentStatus.ACTIVE);
        reply.setDeletedAt(null);
        applyCommentMedias(reply, mediaDescriptors);
        return commentMapper.toResponse(commentRepository.save(reply));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<CommentResponse> searchReplies(UUID commentId, CommentCursorPageRequest request) {
        loadCommentOrThrow(commentId);
        CursorAnchor cursorAnchor = parseCursor(request.getCursor());
        int size = request.getLimitedSize();

        List<Comment> comments = commentRepository.searchRepliesByParentAndCursor(
                commentId,
                cursorAnchor.createdAt(),
                cursorAnchor.id(),
                request.getNormalizedKeyword(),
                PageRequest.of(0, size + 1));

        return toCursorPageResponse(comments, size, commentMapper::toResponse);
    }

    @Override
    public CommentResponse updateComment(UUID commentId, UpdateCommentRequest request, HttpServletRequest httpRequest) {
        UUID userId = RequestAuthUtils.requireUserId(httpRequest);
        String content = normalizeRequiredText(request.getContent());
        List<MediaUrlUtils.MediaDescriptor> mediaDescriptors = normalizeMediaDescriptors(request.getMediaUrls());

        Comment comment = loadCommentOrThrow(commentId);
        ensureCommentOwner(comment, userId);

        commentMapper.updateEntityFromRequest(comment, request);
        comment.setContent(content);
        comment.setStatus(CommentStatus.ACTIVE);
        comment.setDeletedAt(null);
        applyCommentMedias(comment, mediaDescriptors);
        return commentMapper.toResponse(commentRepository.save(comment));
    }

    @Override
    public ActionMessageResponse deleteComment(UUID commentId, HttpServletRequest httpRequest) {
        UUID userId = RequestAuthUtils.requireUserId(httpRequest);
        Comment comment = loadCommentOrThrow(commentId);
        ensureCommentOwner(comment, userId);

        comment.setIsDeleted(true);
        comment.setDeletedAt(LocalDateTime.now());
        comment.setStatus(CommentStatus.HIDDEN);
        commentRepository.save(comment);
        return ActionMessageResponse.builder()
                .message("Comment deleted successfully")
                .build();
    }

    private void ensureReviewExists(UUID reviewId) {
        if (reviewRepository.findByIdAndIsDeletedFalse(reviewId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    private Comment loadCommentOrThrow(UUID commentId) {
        return commentRepository.findByIdAndIsDeletedFalse(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void ensureCommentOwner(Comment comment, UUID userId) {
        if (comment == null || comment.getUserId() == null || !comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private String normalizeRequiredText(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return value.trim();
    }

    private List<MediaUrlUtils.MediaDescriptor> normalizeMediaDescriptors(List<String> mediaUrls) {
        List<MediaUrlUtils.MediaDescriptor> descriptors = MediaUrlUtils.normalize(mediaUrls);
        if (descriptors.size() > 5) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return descriptors;
    }

    private void applyCommentMedias(Comment comment, List<MediaUrlUtils.MediaDescriptor> mediaDescriptors) {
        comment.getMedias().clear();
        for (MediaUrlUtils.MediaDescriptor descriptor : mediaDescriptors) {
            comment.getMedias().add(CommentMedia.builder()
                    .comment(comment)
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

    private <T> CursorPageResponse<T> toCursorPageResponse(List<Comment> items, int limit, java.util.function.Function<Comment, T> mapper) {
        boolean hasNext = items.size() > limit;
        List<Comment> content = hasNext ? new ArrayList<>(items.subList(0, limit)) : new ArrayList<>(items);
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

    private record CursorAnchor(LocalDateTime createdAt, UUID id) {
    }
}
