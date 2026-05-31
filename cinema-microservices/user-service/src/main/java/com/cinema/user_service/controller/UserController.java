package com.cinema.user_service.controller;

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

    @PostMapping("/customers")
    public ResponseEntity<APIResponse<ActionMessageResponse>> createCustomerProfile(
            @Valid @RequestBody RegisterCustomerRequest registerCustomerRequest) {
        ActionMessageResponse registerCustomerResponse = userService.createCustomerProfile(registerCustomerRequest);
        return created(registerCustomerResponse);
    }

    @PostMapping("/managers")
    public ResponseEntity<APIResponse<ActionMessageResponse>> createManagerProfile(
            @Valid @RequestBody RegisterManagerRequest registerManagerRequest) {
        ActionMessageResponse registerManagerResponse = userService.createManagerProfile(registerManagerRequest);
        return created(registerManagerResponse);
    }

    @PostMapping("/staffs")
    public ResponseEntity<APIResponse<ActionMessageResponse>> createStaffProfile(
            @Valid @RequestBody RegisterStaffRequest registerStaffRequest) {
        ActionMessageResponse registerStaffResponse = userService.createStaffProfile(registerStaffRequest);
        return created(registerStaffResponse);
    }

    @PutMapping("/customers")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateCustomerProfile(
            @Valid @RequestBody UpdateCustomerRequest updateCustomerRequest,
            HttpServletRequest request) {
        ActionMessageResponse updateCustomerResponse = userService.updateCustomerProfile(updateCustomerRequest,
                request);
        return ok(updateCustomerResponse);
    }

    @PutMapping("/managers")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateManagerProfile(
            @Valid @RequestBody UpdateManagerRequest updateManagerRequest,
            HttpServletRequest request) {
        ActionMessageResponse updateManagerResponse = userService.updateManagerProfile(updateManagerRequest,
                request);
        return ok(updateManagerResponse);
    }

    @PutMapping("/staffs")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateStaffProfile(
            @Valid @RequestBody UpdateStaffRequest updateStaffRequest,
            HttpServletRequest request) {
        ActionMessageResponse updateStaffResponse = userService.updateStaffProfile(updateStaffRequest, request);
        return ok(updateStaffResponse);
    }

    @DeleteMapping("/customers/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> deleteCustomerProfile(
            @PathVariable UUID id,
            HttpServletRequest request) {
        ActionMessageResponse response = userService.deleteCustomerProfile(id, request);
        return ok(response);
    }

    @DeleteMapping("/managers/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> deleteManagerProfile(
            @PathVariable UUID id,
            HttpServletRequest request) {
        ActionMessageResponse response = userService.deleteManagerProfile(id, request);
        return ok(response);
    }

    @DeleteMapping("/staffs/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> deleteStaffProfile(
            @PathVariable UUID id,
            HttpServletRequest request) {
        ActionMessageResponse response = userService.deleteStaffProfile(id, request);
        return ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<APIResponse<UserResponse>> getMyProfile(HttpServletRequest request) {
        UserResponse userResponse = userService.getMyProfile(request);
        return ok(userResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<UserResponse>> getUserById(
            @PathVariable UUID id,
            HttpServletRequest request) {
        UserResponse userResponse = userService.getUserById(id, request);
        return ok(userResponse);
    }

    @PostMapping("/staffs/search")
    public ResponseEntity<APIResponse<PageResponse<UserResponse>>> getAllStaff(
            @Valid @RequestBody PageRequest<?> pageRequest,
            HttpServletRequest request) {
        PageResponse<UserResponse> staffList = userService.getAllStaff(pageRequest, request);
        return ok(staffList);
    }

    @PostMapping("/customers/search")
    public ResponseEntity<APIResponse<PageResponse<UserResponse>>> getAllCustomer(
            @Valid @RequestBody PageRequest<?> pageRequest,
            HttpServletRequest request) {
        PageResponse<UserResponse> customerList = userService.getAllCustomer(pageRequest, request);
        return ok(customerList);
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
