package com.cinema.payment_service.repository;

import com.cinema.payment_service.entity.PromotionFilm;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PromotionFilmRepository extends JpaRepository<PromotionFilm, UUID> {
    List<PromotionFilm> findAllByPromotionId(UUID promotionId);

    List<PromotionFilm> findAllByPromotionIdIn(Collection<UUID> promotionIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from PromotionFilm pf where pf.promotionId = :promotionId")
    void deleteByPromotionId(@Param("promotionId") UUID promotionId);
}
