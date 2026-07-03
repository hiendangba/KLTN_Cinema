package com.cinema.film_service.repository;

import com.cinema.film_service.entity.FilmType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FilmTypeRepository extends JpaRepository<FilmType, UUID> {
    Optional<FilmType> findByIdAndIsDeletedFalse(UUID id);

    Optional<FilmType> findByNameIgnoreCaseAndIsDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndIdNotAndIsDeletedFalse(String name, UUID id);

    List<FilmType> findAllByIsDeletedFalseOrderByNameAsc();

    List<FilmType> findAllByIdInAndIsDeletedFalse(List<UUID> ids);
}
