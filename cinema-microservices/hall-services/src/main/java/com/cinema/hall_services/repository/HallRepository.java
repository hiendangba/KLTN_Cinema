package com.cinema.hall_services.repository;

import com.cinema.hall_services.entity.Hall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HallRepository extends JpaRepository<Hall, UUID> {
    Optional<Hall> findByIdAndIsDeletedFalse(UUID id);

    boolean existsByCinemaIdAndNameIgnoreCaseAndIsDeletedFalse(UUID cinemaId, String name);
}
