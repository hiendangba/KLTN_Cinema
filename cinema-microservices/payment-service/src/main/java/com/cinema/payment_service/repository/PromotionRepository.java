package com.cinema.payment_service.repository;

import com.cinema.payment_service.entity.Promotion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromotionRepository extends JpaRepository<Promotion, UUID> {
    Optional<Promotion> findByCodeIgnoreCaseAndIsDeletedFalse(String code);

    boolean existsByCodeIgnoreCaseAndIsDeletedFalse(String code);

    boolean existsByCodeIgnoreCaseAndIsDeletedFalseAndIdNot(String code, UUID id);

    List<Promotion> findAllByIsDeletedFalse();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Promotion p
            where p.id = :id
              and (p.isDeleted = false or p.isDeleted is null)
            """)
    Optional<Promotion> findByIdForUpdate(@Param("id") UUID id);
}
