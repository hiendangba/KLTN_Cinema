package com.cinema.hall_service.repository;

import com.cinema.hall_service.entity.Hall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface HallRepository extends JpaRepository<Hall, UUID> {
    Optional<Hall> findByIdAndIsDeletedFalse(UUID id);

    boolean existsByCinemaIdAndNameIgnoreCaseAndIsDeletedFalse(UUID cinemaId, String name);

    boolean existsByCinemaIdAndNameIgnoreCaseAndIdNotAndIsDeletedFalse(UUID cinemaId, String name, UUID id);

    List<Hall> findAllByCinemaIdAndIsDeletedFalse(UUID cinemaId);
}
