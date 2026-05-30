package com.cinema.identity_service.services.google;

import lombok.Getter;

@Getter
public class GoogleOAuthFlowException extends RuntimeException {
    private final String reason;

    public GoogleOAuthFlowException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public GoogleOAuthFlowException(String reason, Throwable cause) {
        super(reason, cause);
        this.reason = reason;
    }
}
