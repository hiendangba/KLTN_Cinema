package com.cinema.identity_service.services;

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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface UserService {
    RegisterCustomerResponse registerCustomer(RegisterCustomerRequest registerCustomerRequest,
            HttpServletResponse response);

    RegisterCustomerResponse createManager(RegisterManagerRequest registerManagerRequest, HttpServletResponse response);

    RegisterCustomerResponse createStaff(RegisterStaffRequest registerStaffRequest, HttpServletResponse response);

    VerifyResponse verifyOTP(VerifyRequest verifyRequest, HttpServletRequest request);

    void resendOTP(HttpServletRequest request);

    ForgotPasswordResponse forgotPassword(ForgotPasswordRequest forgotPasswordRequest, HttpServletResponse response);

    ChangePasswordResponse changePassword(ChangePasswordRequest changePasswordRequest, HttpServletResponse response);

    LoginResponse login(LoginRequest loginRequest, HttpServletResponse response);

    void logout(HttpServletRequest request);

    LoginResponse refreshToken(HttpServletRequest request, HttpServletResponse response);
}
