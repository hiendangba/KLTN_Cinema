package com.cinema.payment_service.repository;

import com.cinema.payment_service.entity.PromotionCinema;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PromotionCinemaRepository extends JpaRepository<PromotionCinema, UUID> {
    List<PromotionCinema> findAllByPromotionId(UUID promotionId);

    List<PromotionCinema> findAllByPromotionIdIn(Collection<UUID> promotionIds);

    void deleteByPromotionId(UUID promotionId);
}
