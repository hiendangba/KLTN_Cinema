package com.cinema.identity_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class GoogleLoginRequest {
    @NotBlank(message = "Google idToken must not be blank")
    private String idToken;
}
