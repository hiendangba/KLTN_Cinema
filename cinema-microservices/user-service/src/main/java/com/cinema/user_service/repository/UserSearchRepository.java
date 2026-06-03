package com.cinema.user_service.repository;

import com.cinema.Enum.UserEnum;
import com.cinema.user_service.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserSearchRepository {
    Page<User> searchByRoleAndKeyword(UserEnum.UserRole role, String keyword, Pageable pageable);

    long countByRoleAndKeyword(UserEnum.UserRole role, String keyword);
}
