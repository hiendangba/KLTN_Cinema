package com.cinema.payment_service.repository;

import com.cinema.payment_service.entity.PromotionCinema;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PromotionCinemaRepository extends JpaRepository<PromotionCinema, UUID> {
    List<PromotionCinema> findAllByPromotionId(UUID promotionId);

    List<PromotionCinema> findAllByPromotionIdIn(Collection<UUID> promotionIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from PromotionCinema pc where pc.promotionId = :promotionId")
    void deleteByPromotionId(@Param("promotionId") UUID promotionId);
}
