package com.cinema.booking_service.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CustomerInfoResponse {
    private String fullName;
    private String email;
    private String phone;
}

