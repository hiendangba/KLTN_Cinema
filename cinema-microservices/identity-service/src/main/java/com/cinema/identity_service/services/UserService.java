package com.cinema.identity_service.services;

import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.identity_service.dto.request.ChangePasswordRequest;
import com.cinema.identity_service.dto.request.ForgotPasswordRequest;
import com.cinema.identity_service.dto.request.LoginRequest;
import com.cinema.identity_service.dto.request.RegisterCustomerRequest;
import com.cinema.identity_service.dto.request.RegisterManagerRequest;
import com.cinema.identity_service.dto.request.RegisterStaffRequest;
import com.cinema.identity_service.dto.request.VerifyRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface UserService {
    ActionMessageResponse registerCustomer(RegisterCustomerRequest registerCustomerRequest,
            HttpServletResponse response);

    ActionMessageResponse createManager(RegisterManagerRequest registerManagerRequest, HttpServletRequest request);

    ActionMessageResponse createStaff(RegisterStaffRequest registerStaffRequest, HttpServletRequest request);

    ActionMessageResponse verifyOTP(VerifyRequest verifyRequest, HttpServletRequest request);

    ActionMessageResponse resendOTP(HttpServletRequest request);

    ActionMessageResponse forgotPassword(ForgotPasswordRequest forgotPasswordRequest, HttpServletResponse response);

    ActionMessageResponse changePassword(ChangePasswordRequest changePasswordRequest, HttpServletRequest request);

    ActionMessageResponse login(LoginRequest loginRequest, HttpServletResponse response);

    ActionMessageResponse logout(HttpServletRequest request, HttpServletResponse response);

    ActionMessageResponse refreshToken(HttpServletRequest request, HttpServletResponse response);
}
