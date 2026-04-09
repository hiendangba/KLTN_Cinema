package com.cinema.showtime_service.repository;

import com.cinema.showtime_service.entity.PricingPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PricingPolicyRepository extends JpaRepository<PricingPolicy, UUID> {
    Optional<PricingPolicy> findByIdAndIsDeletedFalse(UUID id);

    Optional<PricingPolicy> findByIdAndCinemaIdAndIsDeletedFalse(UUID id, UUID cinemaId);

    List<PricingPolicy> findAllByCinemaIdAndIsDeletedFalseOrderByTimeCreatedDesc(UUID cinemaId);
}
