package com.cinema.identity_service.controller;

import com.cinema.Enum.SuccessMessage;
import com.cinema.controller.BaseController;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
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
        return created(SuccessMessage.REGISTERED, registerCustomerResponse);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<APIResponse<ActionMessageResponse>> verifyOTP(@Valid @RequestBody VerifyRequest verifyRequest,
        HttpServletRequest request,
        HttpServletResponse response) {
        ActionMessageResponse verifyResponse = userService.verifyOTP(verifyRequest, request, response);
        return ok(SuccessMessage.OTP_VERIFIED, verifyResponse);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<APIResponse<ActionMessageResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest forgotPasswordRequest, HttpServletResponse response) {
        ActionMessageResponse forgotPasswordResponse = userService.forgotPassword(forgotPasswordRequest, response);
        return ok(SuccessMessage.AUTH_PASSWORD_RESET, forgotPasswordResponse);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<APIResponse<ActionMessageResponse>> resendOTP(HttpServletRequest request) {
        ActionMessageResponse response = userService.resendOTP(request);
        return ok(SuccessMessage.OTP_RESENT, response);
    }

    @PostMapping("/login")
    public ResponseEntity<APIResponse<ActionMessageResponse>> login(@Valid @RequestBody LoginRequest loginRequest,
                                                                    HttpServletResponse response) {
        ActionMessageResponse actionMessageResponse = userService.login(loginRequest, response);
        return ok(SuccessMessage.LOGGED_IN, actionMessageResponse);
    }

    @PostMapping("/google/login")
    public ResponseEntity<APIResponse<ActionMessageResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest googleLoginRequest,
            HttpServletResponse response) {
        ActionMessageResponse actionMessageResponse = userService.googleLogin(googleLoginRequest, response);
        return ok(SuccessMessage.AUTH_GOOGLE_LOGIN, actionMessageResponse);
    }

    @GetMapping("/google/authorize")
    public void googleAuthorize(HttpServletResponse response) {
        userService.googleAuthorize(response);
    }

    @GetMapping("/google/callback")
    public void googleCallback(@RequestParam(required = false) String code,
                                @RequestParam(required = false) String state,
                                @RequestParam(required = false) String error,
                                HttpServletResponse response) {
        userService.googleCallback(code, state, error, response);
    }

    @PostMapping("/logout")
    public ResponseEntity<APIResponse<ActionMessageResponse>> logout(HttpServletRequest request,
                                                                     HttpServletResponse httpServletResponse) {
        ActionMessageResponse response = userService.logout(request, httpServletResponse);
        return ok(SuccessMessage.LOGGED_OUT, response);
    }

    @PostMapping("/refresh_token")
    public ResponseEntity<APIResponse<ActionMessageResponse>> refreshToken(HttpServletRequest request,
                                                                           HttpServletResponse response) {
        ActionMessageResponse actionMessageResponse = userService.refreshToken(request, response);
        return ok(SuccessMessage.TOKEN_REFRESHED, actionMessageResponse);
    }

    @PostMapping("/manager")
    public ResponseEntity<APIResponse<ActionMessageResponse>> createManager(
            @Valid @RequestBody RegisterManagerRequest registerManagerRequest, HttpServletRequest request) {
        ActionMessageResponse registerCustomerResponse = userService.createManager(registerManagerRequest, request);
        return created(SuccessMessage.PROFILE_CREATED, registerCustomerResponse);
    }

    @PostMapping("/staff")
    public ResponseEntity<APIResponse<ActionMessageResponse>> createStaff(
            @Valid @RequestBody RegisterStaffRequest registerStaffRequest, HttpServletRequest request) {
        ActionMessageResponse registerCustomerResponse = userService.createStaff(registerStaffRequest, request);
        return created(SuccessMessage.PROFILE_CREATED, registerCustomerResponse);
    }
}
