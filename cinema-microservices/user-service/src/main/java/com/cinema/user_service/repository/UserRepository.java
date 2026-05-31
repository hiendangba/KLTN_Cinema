package com.cinema.user_service.repository;

import com.cinema.Enum.UserEnum;
import com.cinema.user_service.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
    Page<User> findByRoleAndIsDeletedFalse(UserEnum.UserRole role, Pageable pageable);
    Optional<User> findByIdAndRoleAndIsDeletedFalse(UUID id, UserEnum.UserRole role);
    Optional<User> findByIdAndRoleAndIsDeletedTrue(UUID id, UserEnum.UserRole role);
    Optional<User> findByIdAndIsDeletedFalse(UUID id);
    boolean existsByIdAndIsDeletedFalse(UUID id);
}
