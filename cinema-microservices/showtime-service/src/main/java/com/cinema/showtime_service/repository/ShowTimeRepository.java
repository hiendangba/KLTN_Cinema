package com.cinema.showtime_service.repository;

import com.cinema.showtime_service.entity.ShowTime;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShowTimeRepository extends JpaRepository<ShowTime, UUID> {

    boolean existsByPricingPolicyIdAndIsDeletedFalse(UUID pricingPolicyId);

    @Query(value = """
            SELECT * FROM show_time s
            WHERE s.hall_id = :hallId
              AND s.start_date_time < :endTime
              AND s.end_date_time > :startTime
              AND s.status IN ('SCHEDULED', 'ONGOING')
              AND s.is_deleted = false
            ORDER BY s.end_date_time DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<ShowTime> findOverlapping(
            @Param("hallId") UUID hallId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query(value = """
            SELECT * FROM show_time s
            WHERE s.hall_id = :hallId
              AND s.id <> :showTimeId
              AND s.start_date_time < :endTime
              AND s.end_date_time > :startTime
              AND s.status IN ('SCHEDULED', 'ONGOING')
              AND s.is_deleted = false
            ORDER BY s.end_date_time DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<ShowTime> findOverlappingExcludingId(
            @Param("showTimeId") UUID showTimeId,
            @Param("hallId") UUID hallId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query(value = """
            SELECT s.id
            FROM show_time s
            WHERE s.hall_id = :hallId
              AND s.status IN ('SCHEDULED', 'ONGOING')
              AND s.is_deleted = false
            """, nativeQuery = true)
    List<UUID> findActiveShowtimeIdsByHallId(@Param("hallId") UUID hallId);

    @Query(value = """
            SELECT DISTINCT s.film_id
            FROM show_time s
            WHERE s.status IN ('SCHEDULED', 'ONGOING')
              AND s.is_deleted = false
            """, nativeQuery = true)
    List<UUID> findActiveFilmIds();
}
