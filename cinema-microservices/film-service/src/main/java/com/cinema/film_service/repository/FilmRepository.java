package com.cinema.film_service.repository;

import com.cinema.film_service.entity.Film;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FilmRepository extends JpaRepository<Film, UUID>, JpaSpecificationExecutor<Film> {
    Optional<Film> findByTitleAndReleaseDateAndIsDeletedFalse(String title, LocalDate releaseDate);

    boolean existsByTitleAndReleaseDateAndIdNotAndIsDeletedFalse(String title, LocalDate releaseDate, UUID id);

    Optional<Film> findByIdAndIsDeletedFalse(UUID id);

    List<Film> findDistinctByTypes_IdAndIsDeletedFalse(UUID typeId);

    List<Film> findDistinctByActors_IdAndIsDeletedFalse(UUID actorId);
}
