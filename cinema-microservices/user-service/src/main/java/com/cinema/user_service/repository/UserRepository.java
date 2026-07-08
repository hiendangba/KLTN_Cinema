package com.cinema.user_service.repository;

import com.cinema.Enum.UserEnum;
import com.cinema.user_service.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, UserSearchRepository {
    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    Page<User> findByRoleAndIsDeletedFalse(UserEnum.UserRole role, Pageable pageable);

    Optional<User> findByPhoneAndRoleAndIsDeletedFalse(String phone, UserEnum.UserRole role);

    Optional<User> findByIdAndRoleAndIsDeletedFalse(UUID id, UserEnum.UserRole role);

    Optional<User> findByIdAndRoleAndIsDeletedTrue(UUID id, UserEnum.UserRole role);

    Optional<User> findByIdAndIsDeletedFalse(UUID id);

    boolean existsByIdAndIsDeletedFalse(UUID id);

    @Modifying
    @Query("""
            update User u
               set u.loyaltyPoints = :loyaltyPoints,
                   u.timeUpdated = :timeUpdated
             where u.id = :userId
               and u.isDeleted = false
            """)
    int updateLoyaltyPoints(
            @Param("userId") UUID userId,
            @Param("loyaltyPoints") Long loyaltyPoints,
            @Param("timeUpdated") LocalDateTime timeUpdated);

    @Modifying
    @Query("""
            update User u
               set u.lifetimePaidAmount = :lifetimePaidAmount,
                   u.timeUpdated = :timeUpdated
             where u.id = :userId
               and u.isDeleted = false
            """)
    int updateLifetimePaidAmount(
            @Param("userId") UUID userId,
            @Param("lifetimePaidAmount") BigDecimal lifetimePaidAmount,
            @Param("timeUpdated") LocalDateTime timeUpdated);
}
