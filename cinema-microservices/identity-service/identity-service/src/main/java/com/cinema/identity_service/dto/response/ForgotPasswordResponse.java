package com.cinema.identity_service.dto.response;
import lombok.Builder;
import lombok.Getter;
@Getter
@Builder
public class ForgotPasswordResponse {
    private String message;
}
