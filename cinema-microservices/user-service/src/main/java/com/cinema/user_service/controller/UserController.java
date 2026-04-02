package com.cinema.user_service.controller;

import com.cinema.controller.BaseController;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.user_service.dto.request.*;
import com.cinema.user_service.dto.response.RegisterCustomerResponse;
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

    @PostMapping("/customers")
    public ResponseEntity<APIResponse<RegisterCustomerResponse>> createCustomerProfile(
            @Valid @RequestBody RegisterCustomerRequest registerCustomerRequest) {
        RegisterCustomerResponse registerCustomerResponse = userService.createCustomerProfile(registerCustomerRequest);
        return created(registerCustomerResponse);
    }

    @PostMapping("/managers")
    public ResponseEntity<APIResponse<RegisterCustomerResponse>> createManagerProfile(
            @Valid @RequestBody RegisterManagerRequest registerManagerRequest) {
        RegisterCustomerResponse registerManagerResponse = userService.createManagerProfile(registerManagerRequest);
        return created(registerManagerResponse);
    }

    @PostMapping("/staffs")
    public ResponseEntity<APIResponse<RegisterCustomerResponse>> createStaffProfile(
            @Valid @RequestBody RegisterStaffRequest registerStaffRequest) {
        RegisterCustomerResponse registerStaffResponse = userService.createStaffProfile(registerStaffRequest);
        return created(registerStaffResponse);
    }

    @PutMapping("/customers")
    public ResponseEntity<APIResponse<RegisterCustomerResponse>> updateCustomerProfile(
            @Valid @RequestBody UpdateCustomerRequest updateCustomerRequest,
            HttpServletRequest request) {
        RegisterCustomerResponse updateCustomerResponse = userService.updateCustomerProfile(updateCustomerRequest,
                request);
        return ok(updateCustomerResponse);
    }

    @PutMapping("/managers")
    public ResponseEntity<APIResponse<RegisterCustomerResponse>> updateManagerProfile(
            @Valid @RequestBody UpdateManagerRequest updateManagerRequest,
            HttpServletRequest request) {
        RegisterCustomerResponse updateManagerResponse = userService.updateManagerProfile(updateManagerRequest,
                request);
        return ok(updateManagerResponse);
    }

    @PutMapping("/staffs")
    public ResponseEntity<APIResponse<RegisterCustomerResponse>> updateStaffProfile(
            @Valid @RequestBody UpdateStaffRequest updateStaffRequest,
            HttpServletRequest request) {
        RegisterCustomerResponse updateStaffResponse = userService.updateStaffProfile(updateStaffRequest, request);
        return ok(updateStaffResponse);
    }

    @PostMapping("/staffs/search")
    public ResponseEntity<APIResponse<PageResponse<UserResponse>>> getAllStaff(
            @Valid @RequestBody PageRequest<?> pageRequest,
            HttpServletRequest request) {
        PageResponse<UserResponse> staffList = userService.getAllStaff(pageRequest, request);
        return ok(staffList);
    }

    @PostMapping("/managers/search")
    public ResponseEntity<APIResponse<PageResponse<UserResponse>>> getAllManager(
            @Valid @RequestBody PageRequest<?> pageRequest,
            HttpServletRequest request) {
        PageResponse<UserResponse> managerList = userService.getAllManager(pageRequest, request);
        return ok(managerList);
    }

    @GetMapping("/exists/{userId}")
    public ResponseEntity<APIResponse<UserExistenceResponse>> checkUserExists(@PathVariable UUID userId) {
        UserExistenceResponse response = userService.checkUserExists(userId);
        return ok(response);
    }

}
