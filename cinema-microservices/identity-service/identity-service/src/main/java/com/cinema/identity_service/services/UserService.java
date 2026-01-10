package com.cinema.identity_service.services;

import com.cinema.identity_service.dto.request.RegisterCustomerRequest;
import com.cinema.identity_service.dto.request.VerifyRequest;
import com.cinema.identity_service.dto.response.RegisterCustomerResponse;
import com.cinema.identity_service.dto.response.VerifyResponse;

public interface UserService {
    RegisterCustomerResponse registerCustomer(RegisterCustomerRequest registerCustomerRequest);
    VerifyResponse verifyOTP(VerifyRequest verifyRequest);
}
