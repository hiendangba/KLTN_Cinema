package com.cinema.identity_service.controller;

import com.cinema.controller.BaseController;
import com.cinema.dto.response.APIResponse;
import com.cinema.identity_service.dto.request.RegisterCustomerRequest;
import com.cinema.identity_service.dto.response.RegisterCustomerResponse;
import com.cinema.identity_service.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController extends BaseController {
    private final UserService userService;
    @PostMapping("/register")
    public ResponseEntity<APIResponse<RegisterCustomerResponse>> register(@RequestBody RegisterCustomerRequest registerCustomerRequest) {
        RegisterCustomerResponse response = userService.registerCustomer(registerCustomerRequest);
        return created(response);
    }
}
