package com.cinema.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    VALIDATION_ERROR("9001", "Validation error", HttpStatus.BAD_REQUEST),
    NOT_FOUND("9002", "Resource not found", HttpStatus.NOT_FOUND),
    UNAUTHORIZED("9003", "Unauthorized", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("9004", "Forbidden", HttpStatus.FORBIDDEN),
    BAD_REQUEST("9005", "Bad request", HttpStatus.BAD_REQUEST),
    INVALID_FORMAT("9007","Invalid format", HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR("9500", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_ERROR("9501", "Database error", HttpStatus.INTERNAL_SERVER_ERROR),
    EXTERNAL_SERVICE_ERROR("9502", "External service error", HttpStatus.SERVICE_UNAVAILABLE),


    EMAIL_EXISTED("4001", "Email đã được đăng ký trước đó", HttpStatus.CONFLICT),
    OTP_ALREADY_SENT("4002","Đã gửi mã OTP rồi. Vui lòng đợi 5 phút sau thử lại!",HttpStatus.CONFLICT),
    OTP_INVALID("4003","Mã OTP bạn vừa nhập không chính xác hoặc đã hết hạn. Hãy thử lại!",HttpStatus.BAD_REQUEST),
    OTP_VERIFY_LIMIT("4004","Bạn đã nhập sai OTP 3 lần vui lòng đợt 5p sau và đăng ký lại",HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}