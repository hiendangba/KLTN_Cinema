package com.cinema.user_service.controller;

import com.cinema.Enum.SuccessMessage;
import com.cinema.controller.BaseController;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.user_service.dto.request.*;
import com.cinema.user_service.dto.response.UserExistenceResponse;
import com.cinema.user_service.dto.response.UserResponse;
import com.cinema.user_service.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController extends BaseController {
    private final UserService userService;

    @PutMapping("/customers")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateCustomerProfile(
            @Valid @RequestBody UpdateCustomerRequest updateCustomerRequest,
            HttpServletRequest request) {
        ActionMessageResponse updateCustomerResponse = userService.updateCustomerProfile(updateCustomerRequest,
                request);
        return ok(SuccessMessage.PROFILE_UPDATED, updateCustomerResponse);
    }

    @PutMapping("/customers/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateCustomerProfileById(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCustomerRequest updateCustomerRequest,
            HttpServletRequest request) {
        ActionMessageResponse response = userService.updateCustomerProfile(id, updateCustomerRequest, request);
        return ok(SuccessMessage.PROFILE_UPDATED, response);
    }

    @PutMapping("/managers")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateManagerProfile(
            @Valid @RequestBody UpdateManagerRequest updateManagerRequest,
            HttpServletRequest request) {
        ActionMessageResponse updateManagerResponse = userService.updateManagerProfile(updateManagerRequest,
                request);
        return ok(SuccessMessage.PROFILE_UPDATED, updateManagerResponse);
    }

    @PutMapping("/managers/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateManagerProfileById(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateManagerRequest updateManagerRequest,
            HttpServletRequest request) {
        ActionMessageResponse response = userService.updateManagerProfile(id, updateManagerRequest, request);
        return ok(SuccessMessage.PROFILE_UPDATED, response);
    }

    @PutMapping("/staffs")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateStaffProfile(
            @Valid @RequestBody UpdateStaffRequest updateStaffRequest,
            HttpServletRequest request) {
        ActionMessageResponse updateStaffResponse = userService.updateStaffProfile(updateStaffRequest, request);
        return ok(SuccessMessage.PROFILE_UPDATED, updateStaffResponse);
    }

    @PutMapping("/staffs/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateStaffProfileById(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStaffRequest updateStaffRequest,
            HttpServletRequest request) {
        ActionMessageResponse response = userService.updateStaffProfile(id, updateStaffRequest, request);
        return ok(SuccessMessage.PROFILE_UPDATED, response);
    }

    @DeleteMapping("/customers/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> deleteCustomerProfile(
            @PathVariable UUID id,
            HttpServletRequest request) {
        ActionMessageResponse response = userService.deleteCustomerProfile(id, request);
        return ok(SuccessMessage.PROFILE_DELETED, response);
    }

    @DeleteMapping("/managers/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> deleteManagerProfile(
            @PathVariable UUID id,
            HttpServletRequest request) {
        ActionMessageResponse response = userService.deleteManagerProfile(id, request);
        return ok(SuccessMessage.PROFILE_DELETED, response);
    }

    @DeleteMapping("/staffs/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> deleteStaffProfile(
            @PathVariable UUID id,
            HttpServletRequest request) {
        ActionMessageResponse response = userService.deleteStaffProfile(id, request);
        return ok(SuccessMessage.PROFILE_DELETED, response);
    }

    @PatchMapping("/customers/{id}/restore")
    public ResponseEntity<APIResponse<ActionMessageResponse>> restoreCustomerProfile(
            @PathVariable UUID id,
            HttpServletRequest request) {
        ActionMessageResponse response = userService.restoreCustomerProfile(id, request);
        return ok(SuccessMessage.PROFILE_RESTORED, response);
    }

    @PatchMapping("/managers/{id}/restore")
    public ResponseEntity<APIResponse<ActionMessageResponse>> restoreManagerProfile(
            @PathVariable UUID id,
            HttpServletRequest request) {
        ActionMessageResponse response = userService.restoreManagerProfile(id, request);
        return ok(SuccessMessage.PROFILE_RESTORED, response);
    }

    @PatchMapping("/staffs/{id}/restore")
    public ResponseEntity<APIResponse<ActionMessageResponse>> restoreStaffProfile(
            @PathVariable UUID id,
            HttpServletRequest request) {
        ActionMessageResponse response = userService.restoreStaffProfile(id, request);
        return ok(SuccessMessage.PROFILE_RESTORED, response);
    }

    @GetMapping("/me")
    public ResponseEntity<APIResponse<UserResponse>> getMyProfile(HttpServletRequest request) {
        UserResponse userResponse = userService.getMyProfile(request);
        return ok(SuccessMessage.USER_FETCHED, userResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<UserResponse>> getUserById(
            @PathVariable UUID id,
            HttpServletRequest request) {
        UserResponse userResponse = userService.getUserById(id, request);
        return ok(SuccessMessage.USER_FETCHED, userResponse);
    }

    @PostMapping("/staffs/search")
    public ResponseEntity<APIResponse<PageResponse<UserResponse>>> getAllStaff(
            @Valid @RequestBody PageRequest<?> pageRequest,
            HttpServletRequest request) {
        PageResponse<UserResponse> staffList = userService.getAllStaff(pageRequest, request);
        return ok(SuccessMessage.USERS_SEARCHED, staffList);
    }

    @PostMapping("/customers/search")
    public ResponseEntity<APIResponse<PageResponse<UserResponse>>> getAllCustomer(
            @Valid @RequestBody PageRequest<?> pageRequest,
            HttpServletRequest request) {
        PageResponse<UserResponse> customerList = userService.getAllCustomer(pageRequest, request);
        return ok(SuccessMessage.USERS_SEARCHED, customerList);
    }

    @PostMapping("/managers/search")
    public ResponseEntity<APIResponse<PageResponse<UserResponse>>> getAllManager(
            @Valid @RequestBody PageRequest<?> pageRequest,
            HttpServletRequest request) {
        PageResponse<UserResponse> managerList = userService.getAllManager(pageRequest, request);
        return ok(SuccessMessage.USERS_SEARCHED, managerList);
    }

    @GetMapping("/exists/{userId}")
    public ResponseEntity<APIResponse<UserExistenceResponse>> checkUserExists(@PathVariable UUID userId) {
        UserExistenceResponse response = userService.checkUserExists(userId);
        return ok(SuccessMessage.USER_EXISTENCE_CHECKED, response);
    }
}
