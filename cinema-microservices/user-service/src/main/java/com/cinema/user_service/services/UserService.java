package com.cinema.user_service.services;

import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.PageResponse;
import com.cinema.user_service.dto.request.*;
import com.cinema.user_service.dto.response.RegisterCustomerResponse;
import com.cinema.user_service.dto.response.UserExistenceResponse;
import com.cinema.user_service.dto.response.UserResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface UserService {
    RegisterCustomerResponse createCustomerProfile(RegisterCustomerRequest request);

    RegisterCustomerResponse createManagerProfile(RegisterManagerRequest request);

    RegisterCustomerResponse createStaffProfile(RegisterStaffRequest request);

    RegisterCustomerResponse updateCustomerProfile(UpdateCustomerRequest request, HttpServletRequest httpRequest);

    RegisterCustomerResponse updateManagerProfile(UpdateManagerRequest request, HttpServletRequest httpRequest);

    RegisterCustomerResponse updateStaffProfile(UpdateStaffRequest request, HttpServletRequest httpRequest);

    UserExistenceResponse checkUserExists(UUID userId);

    PageResponse<UserResponse> getAllStaff(PageRequest<?> pageRequest, HttpServletRequest request);

    PageResponse<UserResponse> getAllManager(PageRequest<?> pageRequest, HttpServletRequest request);
}
