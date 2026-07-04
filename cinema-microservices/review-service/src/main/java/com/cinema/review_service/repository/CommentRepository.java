package com.cinema.review_service.repository;

import com.cinema.review_service.entity.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    @EntityGraph(attributePaths = "medias")
    Optional<Comment> findByIdAndIsDeletedFalse(UUID id);

    @EntityGraph(attributePaths = "medias")
    Optional<Comment> findByIdAndReviewIdAndIsDeletedFalse(UUID id, UUID reviewId);

    @EntityGraph(attributePaths = "medias")
    @Query("""
            SELECT c
            FROM Comment c
            WHERE c.reviewId = :reviewId
              AND c.parentCommentId IS NULL
              AND c.isDeleted = false
              AND (:keyword IS NULL OR lower(coalesce(c.content, '')) LIKE concat('%', :keyword, '%'))
              AND (
                   :cursorCreatedAt IS NULL
                   OR c.createdAt < :cursorCreatedAt
                   OR (c.createdAt = :cursorCreatedAt AND c.id < :cursorId)
              )
            ORDER BY c.createdAt DESC, c.id DESC
            """)
    List<Comment> searchRootCommentsByReviewAndCursor(
            @Param("reviewId") UUID reviewId,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            @Param("keyword") String keyword,
            Pageable pageable);

    @EntityGraph(attributePaths = "medias")
    @Query("""
            SELECT c
            FROM Comment c
            WHERE c.parentCommentId = :parentCommentId
              AND c.isDeleted = false
              AND (:keyword IS NULL OR lower(coalesce(c.content, '')) LIKE concat('%', :keyword, '%'))
              AND (
                   :cursorCreatedAt IS NULL
                   OR c.createdAt < :cursorCreatedAt
                   OR (c.createdAt = :cursorCreatedAt AND c.id < :cursorId)
              )
            ORDER BY c.createdAt DESC, c.id DESC
            """)
    List<Comment> searchRepliesByParentAndCursor(
            @Param("parentCommentId") UUID parentCommentId,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            @Param("keyword") String keyword,
            Pageable pageable);
}
