package com.cinema.cinema_service.repository;

import com.cinema.cinema_service.entity.CinemaStaff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CinemaStaffRepository extends JpaRepository<CinemaStaff, UUID> {

    List<CinemaStaff> findByCinemaIdAndActiveTrue(UUID cinemaId);

    List<CinemaStaff> findByCinemaIdInAndActiveTrue(List<UUID> cinemaIds);

    List<CinemaStaff> findByCinemaId(UUID cinemaId);

    Optional<CinemaStaff> findByCinemaIdAndStaffId(UUID cinemaId, UUID staffId);

    Optional<CinemaStaff> findByStaffId(UUID staffId);

    boolean existsByStaffId(UUID staffId);
}
