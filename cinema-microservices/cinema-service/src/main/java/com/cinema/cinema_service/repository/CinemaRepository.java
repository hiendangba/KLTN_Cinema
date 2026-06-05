package com.cinema.cinema_service.repository;

import com.cinema.cinema_service.entity.Cinema;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CinemaRepository extends JpaRepository<Cinema, UUID> {

    Optional<Cinema> findByIdAndIsDeletedFalse(UUID id);

    List<Cinema> findAllByManagerIdAndIsDeletedFalseOrderByCreatedAtDesc(UUID managerId);

    List<Cinema> findAllByIsDeletedFalseOrderByCreatedAtDesc();

    boolean existsByCodeIgnoreCaseAndIsDeletedFalse(String code);

    boolean existsByCodeIgnoreCaseAndIdNotAndIsDeletedFalse(String code, UUID id);
}
