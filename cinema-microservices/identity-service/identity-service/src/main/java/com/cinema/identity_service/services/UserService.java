package com.cinema.identity_service.services;

import com.cinema.identity_service.dto.request.RegisterCustomerRequest;
import com.cinema.identity_service.dto.response.RegisterCustomerResponse;

public interface UserService {
    RegisterCustomerResponse registerCustomer(RegisterCustomerRequest registerCustomerRequest);
}
