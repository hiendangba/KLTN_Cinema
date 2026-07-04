package com.cinema.review_service.repository;

import com.cinema.review_service.entity.Review;
import com.cinema.review_service.entity.enums.ReviewStatus;
import com.cinema.review_service.repository.projection.ReviewRatingSummaryView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    @EntityGraph(attributePaths = "medias")
    Optional<Review> findByIdAndIsDeletedFalse(UUID id);

    @EntityGraph(attributePaths = "medias")
    Optional<Review> findByUserIdAndFilmIdAndIsDeletedFalse(UUID userId, UUID filmId);

    @EntityGraph(attributePaths = "medias")
    @Query("""
            SELECT r
            FROM Review r
            WHERE r.filmId = :filmId
              AND r.isDeleted = false
              AND (:keyword IS NULL OR lower(coalesce(r.title, '')) LIKE concat('%', :keyword, '%')
                   OR lower(coalesce(r.content, '')) LIKE concat('%', :keyword, '%'))
              AND (
                   :cursorCreatedAt IS NULL
                   OR r.createdAt < :cursorCreatedAt
                   OR (r.createdAt = :cursorCreatedAt AND r.id < :cursorId)
              )
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<Review> searchByFilmIdAndCursor(
            @Param("filmId") UUID filmId,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("""
            SELECT r.filmId as filmId,
                   AVG(r.rating) as averageRating,
                   COUNT(r.id) as reviewCount
            FROM Review r
            WHERE r.filmId IN :filmIds
              AND r.isDeleted = false
              AND r.status = :status
            GROUP BY r.filmId
            """)
    List<ReviewRatingSummaryView> findRatingSummariesByFilmIds(
            @Param("filmIds") List<UUID> filmIds,
            @Param("status") ReviewStatus status);
}
