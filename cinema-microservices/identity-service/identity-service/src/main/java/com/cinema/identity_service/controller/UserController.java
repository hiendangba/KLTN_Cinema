package com.cinema.identity_service.controller;

import com.cinema.controller.BaseController;
import com.cinema.dto.response.APIResponse;
import com.cinema.identity_service.dto.request.RegisterCustomerRequest;
import com.cinema.identity_service.dto.request.VerifyRequest;
import com.cinema.identity_service.dto.response.RegisterCustomerResponse;
import com.cinema.identity_service.dto.response.VerifyResponse;
import com.cinema.identity_service.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController extends BaseController {
    private final UserService userService;
    @PostMapping("/register")
    public ResponseEntity<APIResponse<RegisterCustomerResponse>> register(@Valid @RequestBody RegisterCustomerRequest registerCustomerRequest) {
        RegisterCustomerResponse registerCustomerResponse = userService.registerCustomer(registerCustomerRequest);
        return created(registerCustomerResponse);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<APIResponse<VerifyResponse>> verifyOTP(@Valid @RequestBody VerifyRequest verifyRequest) {
        VerifyResponse verifyResponse = userService.verifyOTP(verifyRequest);
        return ok(verifyResponse);
    }
}
