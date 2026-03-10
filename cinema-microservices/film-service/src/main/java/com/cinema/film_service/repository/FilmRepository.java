package com.cinema.film_service.repository;

import com.cinema.film_service.entity.Film;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FilmRepository extends JpaRepository<Film, UUID>, JpaSpecificationExecutor<Film> {
    Optional<Film> findByTitleAndReleaseDate(String title, LocalDate releaseDate);

    boolean existsByTitleAndReleaseDateAndIdNot(String title, LocalDate releaseDate, UUID id);

    Optional<Film> findByIdAndIsDeletedFalse(UUID id);

    @Query(value = "SELECT f FROM Film f WHERE f.isDeleted = false " +
            "AND (:cursor IS NULL OR f.id > :cursor) " +
            "AND (COALESCE(:keyword, '') = '' " +
            "OR LOWER(f.title) LIKE %:keyword% OR LOWER(f.director) LIKE %:keyword% OR LOWER(f.actor) LIKE %:keyword% OR LOWER(f.description) LIKE %:keyword%) " +
            "ORDER BY f.id ASC")
    List<Film> findByCursorAndKeyword(UUID cursor, String keyword, int limit);
}
