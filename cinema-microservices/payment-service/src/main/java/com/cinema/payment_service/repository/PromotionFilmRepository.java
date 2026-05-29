package com.cinema.payment_service.repository;

import com.cinema.payment_service.entity.PromotionFilm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PromotionFilmRepository extends JpaRepository<PromotionFilm, UUID> {
    List<PromotionFilm> findAllByPromotionId(UUID promotionId);

    List<PromotionFilm> findAllByPromotionIdIn(Collection<UUID> promotionIds);

    void deleteByPromotionId(UUID promotionId);
}
