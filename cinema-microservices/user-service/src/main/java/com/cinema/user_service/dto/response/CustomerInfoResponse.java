package com.cinema.user_service.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CustomerInfoResponse {
    private UUID id;
    private String name;
    private String phone;
}
