package com.cinema.user_service.services;

import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.user_service.dto.request.*;
import com.cinema.user_service.dto.response.UserExistenceResponse;
import com.cinema.user_service.dto.response.UserResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface UserService {
    ActionMessageResponse createCustomerProfile(RegisterCustomerRequest request);

    ActionMessageResponse createManagerProfile(RegisterManagerRequest request);

    ActionMessageResponse createStaffProfile(RegisterStaffRequest request);

    ActionMessageResponse updateCustomerProfile(UpdateCustomerRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updateManagerProfile(UpdateManagerRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updateStaffProfile(UpdateStaffRequest request, HttpServletRequest httpRequest);

    UserResponse getMyProfile(HttpServletRequest request);

    UserResponse getUserById(UUID userId, HttpServletRequest httpRequest);

    UserResponse getUserById(UUID userId);

    UserExistenceResponse checkUserExists(UUID userId);

    PageResponse<UserResponse> getAllStaff(PageRequest<?> pageRequest, HttpServletRequest request);

    PageResponse<UserResponse> getAllCustomer(PageRequest<?> pageRequest, HttpServletRequest request);

    PageResponse<UserResponse> getAllManager(PageRequest<?> pageRequest, HttpServletRequest request);
}
