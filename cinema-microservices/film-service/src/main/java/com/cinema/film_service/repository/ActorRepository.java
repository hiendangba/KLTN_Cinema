package com.cinema.film_service.repository;

import com.cinema.film_service.entity.Actor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActorRepository extends JpaRepository<Actor, UUID> {
    Optional<Actor> findByIdAndIsDeletedFalse(UUID id);

    List<Actor> findAllByNameIgnoreCaseAndIsDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndIsDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndIdNotAndIsDeletedFalse(String name, UUID id);

    List<Actor> findAllByIsDeletedFalseOrderByNameAsc();

    List<Actor> findAllByIdInAndIsDeletedFalse(List<UUID> ids);
}
