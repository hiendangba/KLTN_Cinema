package com.cinema.grpc;

import com.cinema.exception.ErrorCode;

public final class GrpcErrorUtils {

    private GrpcErrorUtils() {
    }

    public static ErrorCode resolve(String errorKey, ErrorCode fallback) {
        if (errorKey == null || errorKey.isBlank()) {
            return fallback;
        }
        try {
            return ErrorCode.valueOf(errorKey);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
