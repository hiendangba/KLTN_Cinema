package com.cinema.identity_service.controller;

import com.cinema.controller.BaseController;
import com.cinema.dto.response.APIResponse;
import com.cinema.identity_service.dto.request.ChangePasswordRequest;
import com.cinema.identity_service.dto.request.ForgotPasswordRequest;
import com.cinema.identity_service.dto.request.LoginRequest;
import com.cinema.identity_service.dto.request.RegisterCustomerRequest;
import com.cinema.identity_service.dto.request.RegisterManagerRequest;
import com.cinema.identity_service.dto.request.RegisterStaffRequest;
import com.cinema.identity_service.dto.request.VerifyRequest;
import com.cinema.identity_service.dto.response.ChangePasswordResponse;
import com.cinema.identity_service.dto.response.ForgotPasswordResponse;
import com.cinema.identity_service.dto.response.LoginResponse;
import com.cinema.identity_service.dto.response.RegisterCustomerResponse;
import com.cinema.identity_service.dto.response.VerifyResponse;
import com.cinema.identity_service.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController extends BaseController {
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<APIResponse<RegisterCustomerResponse>> register(
            @Valid @RequestBody RegisterCustomerRequest registerCustomerRequest, HttpServletResponse response) {
        RegisterCustomerResponse registerCustomerResponse = userService.registerCustomer(registerCustomerRequest,
                response);
        return created(registerCustomerResponse);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<APIResponse<VerifyResponse>> verifyOTP(@Valid @RequestBody VerifyRequest verifyRequest,
            HttpServletRequest request) {
        VerifyResponse verifyResponse = userService.verifyOTP(verifyRequest, request);
        return ok(verifyResponse);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<APIResponse<ForgotPasswordResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest forgotPasswordRequest, HttpServletResponse response) {
        ForgotPasswordResponse forgotPasswordResponse = userService.forgotPassword(forgotPasswordRequest, response);
        return ok(forgotPasswordResponse);
    }

    @PostMapping("/change-password")
    public ResponseEntity<APIResponse<ChangePasswordResponse>> changePassword(
            @Valid @RequestBody ChangePasswordRequest changePasswordRequest, HttpServletRequest request) {
        ChangePasswordResponse changePasswordResponse = userService.changePassword(changePasswordRequest, request);
        return ok(changePasswordResponse);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<APIResponse<LoginResponse>> resendOTP(HttpServletRequest request) {
        userService.resendOTP(request);
        return ok(null);
    }

    @PostMapping("/login")
    public ResponseEntity<APIResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest,
            HttpServletResponse response) {
        LoginResponse loginResponse = userService.login(loginRequest, response);
        return ok(loginResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<APIResponse<Void>> logout(HttpServletRequest request) {
        userService.logout(request);
        return ok(null);
    }

    @PostMapping("/refresh_token")
    public ResponseEntity<APIResponse<LoginResponse>> refreshToken(HttpServletRequest request,
            HttpServletResponse response) {
        LoginResponse loginResponse = userService.refreshToken(request, response);
        return ok(loginResponse);
    }

    @PostMapping("/manager")
    public ResponseEntity<APIResponse<RegisterCustomerResponse>> createManager(
            @Valid @RequestBody RegisterManagerRequest registerManagerRequest, HttpServletRequest request) {
        RegisterCustomerResponse registerCustomerResponse = userService.createManager(registerManagerRequest, request);
        return created(registerCustomerResponse);
    }

    @PostMapping("/staff")
    public ResponseEntity<APIResponse<RegisterCustomerResponse>> createStaff(
            @Valid @RequestBody RegisterStaffRequest registerStaffRequest, HttpServletRequest request) {
        RegisterCustomerResponse registerCustomerResponse = userService.createStaff(registerStaffRequest, request);
        return created(registerCustomerResponse);
    }

    // auth-check moved to internal controller
}
