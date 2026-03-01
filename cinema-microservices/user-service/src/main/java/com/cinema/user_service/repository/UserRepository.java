package com.cinema.user_service.repository;

import com.cinema.Enum.UserEnum;
import com.cinema.user_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
    List<User> findByRole(UserEnum.UserRole role);
    Optional<User> findByIdAndRole(UUID id, UserEnum.UserRole role);
}