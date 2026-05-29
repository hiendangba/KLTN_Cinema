package com.cinema.identity_service.controller;

import com.cinema.controller.BaseController;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.identity_service.dto.request.ChangePasswordRequest;
import com.cinema.identity_service.dto.request.ForgotPasswordRequest;
import com.cinema.identity_service.dto.request.GoogleLoginRequest;
import com.cinema.identity_service.dto.request.LoginRequest;
import com.cinema.identity_service.dto.request.RegisterCustomerRequest;
import com.cinema.identity_service.dto.request.RegisterManagerRequest;
import com.cinema.identity_service.dto.request.RegisterStaffRequest;
import com.cinema.identity_service.dto.request.VerifyRequest;
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
    public ResponseEntity<APIResponse<ActionMessageResponse>> register(
            @Valid @RequestBody RegisterCustomerRequest registerCustomerRequest,
            HttpServletResponse response) {
        ActionMessageResponse registerCustomerResponse = userService.registerCustomer(registerCustomerRequest, response);
        return created(registerCustomerResponse);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<APIResponse<ActionMessageResponse>> verifyOTP(@Valid @RequestBody VerifyRequest verifyRequest,
                                                                        HttpServletRequest request) {
        ActionMessageResponse verifyResponse = userService.verifyOTP(verifyRequest, request);
        return ok(verifyResponse);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<APIResponse<ActionMessageResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest forgotPasswordRequest, HttpServletResponse response) {
        ActionMessageResponse forgotPasswordResponse = userService.forgotPassword(forgotPasswordRequest, response);
        return ok(forgotPasswordResponse);
    }

    @PostMapping("/change-password")
    public ResponseEntity<APIResponse<ActionMessageResponse>> changePassword(
            @Valid @RequestBody ChangePasswordRequest changePasswordRequest, HttpServletRequest request) {
        ActionMessageResponse changePasswordResponse = userService.changePassword(changePasswordRequest, request);
        return ok(changePasswordResponse);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<APIResponse<ActionMessageResponse>> resendOTP(HttpServletRequest request) {
        ActionMessageResponse response = userService.resendOTP(request);
        return ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<APIResponse<ActionMessageResponse>> login(@Valid @RequestBody LoginRequest loginRequest,
                                                                    HttpServletResponse response) {
        ActionMessageResponse actionMessageResponse = userService.login(loginRequest, response);
        return ok(actionMessageResponse);
    }

    @PostMapping("/google/login")
    public ResponseEntity<APIResponse<ActionMessageResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest googleLoginRequest,
            HttpServletResponse response) {
        ActionMessageResponse actionMessageResponse = userService.googleLogin(googleLoginRequest, response);
        return ok(actionMessageResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<APIResponse<ActionMessageResponse>> logout(HttpServletRequest request,
                                                                     HttpServletResponse httpServletResponse) {
        ActionMessageResponse response = userService.logout(request, httpServletResponse);
        return ok(response);
    }

    @PostMapping("/refresh_token")
    public ResponseEntity<APIResponse<ActionMessageResponse>> refreshToken(HttpServletRequest request,
                                                                           HttpServletResponse response) {
        ActionMessageResponse actionMessageResponse = userService.refreshToken(request, response);
        return ok(actionMessageResponse);
    }

    @PostMapping("/manager")
    public ResponseEntity<APIResponse<ActionMessageResponse>> createManager(
            @Valid @RequestBody RegisterManagerRequest registerManagerRequest, HttpServletRequest request) {
        ActionMessageResponse registerCustomerResponse = userService.createManager(registerManagerRequest, request);
        return created(registerCustomerResponse);
    }

    @PostMapping("/staff")
    public ResponseEntity<APIResponse<ActionMessageResponse>> createStaff(
            @Valid @RequestBody RegisterStaffRequest registerStaffRequest, HttpServletRequest request) {
        ActionMessageResponse registerCustomerResponse = userService.createStaff(registerStaffRequest, request);
        return created(registerCustomerResponse);
    }
}
