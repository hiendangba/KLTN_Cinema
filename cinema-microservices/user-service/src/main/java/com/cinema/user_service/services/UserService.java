package com.cinema.user_service.services;

import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.Enum.UserEnum;
import com.cinema.user_service.dto.request.*;
import com.cinema.user_service.dto.response.UserExistenceResponse;
import com.cinema.user_service.dto.response.UserResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface UserService {
    ActionMessageResponse createCustomerProfile(RegisterCustomerRequest request);

    ActionMessageResponse createManagerProfile(RegisterManagerRequest request);

    ActionMessageResponse createStaffProfile(RegisterStaffRequest request);

    ActionMessageResponse changePassword(ChangePasswordRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updateCustomerProfile(UpdateCustomerRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updateCustomerProfile(UUID userId, UpdateCustomerRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updateManagerProfile(UpdateManagerRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updateManagerProfile(UUID userId, UpdateManagerRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updateStaffProfile(UpdateStaffRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updateStaffProfile(UUID userId, UpdateStaffRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse deleteCustomerProfile(UUID userId, HttpServletRequest httpRequest);

    ActionMessageResponse deleteCustomerProfile(UUID userId, UUID actorId, UserEnum.UserRole actorRole);

    ActionMessageResponse deleteManagerProfile(UUID userId, HttpServletRequest httpRequest);

    ActionMessageResponse deleteManagerProfile(UUID userId, UUID actorId, UserEnum.UserRole actorRole);

    ActionMessageResponse deleteStaffProfile(UUID userId, HttpServletRequest httpRequest);

    ActionMessageResponse deleteStaffProfile(UUID userId, UUID actorId, UserEnum.UserRole actorRole);

    ActionMessageResponse restoreCustomerProfile(UUID userId, HttpServletRequest httpRequest);

    ActionMessageResponse restoreCustomerProfile(UUID userId, UUID actorId, UserEnum.UserRole actorRole);

    ActionMessageResponse restoreManagerProfile(UUID userId, HttpServletRequest httpRequest);

    ActionMessageResponse restoreManagerProfile(UUID userId, UUID actorId, UserEnum.UserRole actorRole);

    ActionMessageResponse restoreStaffProfile(UUID userId, HttpServletRequest httpRequest);

    ActionMessageResponse restoreStaffProfile(UUID userId, UUID actorId, UserEnum.UserRole actorRole);

    UserResponse getMyProfile(HttpServletRequest request);

    UserResponse getUserById(UUID userId, HttpServletRequest httpRequest);

    UserResponse getUserById(UUID userId);

    UserExistenceResponse checkUserExists(UUID userId);

    PageResponse<UserResponse> getAllStaff(PageRequest<?> pageRequest, HttpServletRequest request);

    PageResponse<UserResponse> getAllCustomer(PageRequest<?> pageRequest, HttpServletRequest request);

    PageResponse<UserResponse> getAllManager(PageRequest<?> pageRequest, HttpServletRequest request);
}
