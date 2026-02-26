package com.cinema.identity_service.entity;

import com.cinema.identity_service.dto.request.ForgotPasswordRequest;
import com.cinema.identity_service.dto.request.RegisterCustomerRequest;
import lombok.*;

import java.time.LocalDateTime;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpData {

    private String verifyToken;
    private String subject;
    private String otpHash;
    private OtpPurpose purpose;

    private LocalDateTime expiredAt;
    private LocalDateTime lastSentAt;
    private RegisterCustomerRequest registerCustomerRequest;
    private ForgotPasswordRequest forgotPasswordRequest;
    @Builder.Default
    private int sendCount = 1;

    @Builder.Default
    private int verifyAttempts = 0;

    public enum OtpPurpose {
        REGISTER,
        FORGOT_PASSWORD
    }
}



