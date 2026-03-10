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
    FORBIDDEN("9004", "Bạn không có đủ quyền truy cập tài nguyên này!", HttpStatus.FORBIDDEN),
    BAD_REQUEST("9005", "Bad request", HttpStatus.BAD_REQUEST),
    INVALID_FORMAT("9007", "Invalid format", HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR("9500", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_ERROR("9501", "Database error", HttpStatus.INTERNAL_SERVER_ERROR),
    EXTERNAL_SERVICE_ERROR("9502", "External service error", HttpStatus.SERVICE_UNAVAILABLE),

    NOT_CREATED("4000", "Không thể tạo thành công vùi lòng liên hệ quản trị viên để được hỗ trợ",
            HttpStatus.BAD_REQUEST),
    ID_EXISTED("4000", "Lỗi trong lúc tạo mới vui lòng thử lại vì ID đã trùng!", HttpStatus.CONFLICT),
    EMAIL_EXISTED("4001", "Email đã được đăng ký trước đó", HttpStatus.CONFLICT),
    USER_NOT_FOUND("4002", "Không tìm thấy người dùng tương ứng", HttpStatus.NOT_FOUND),
    OTP_ALREADY_SENT("4003", "Đã gửi mã OTP rồi. Vui lòng đợi 5 phút sau thử lại!", HttpStatus.CONFLICT),
    OTP_INVALID("4004", "Mã OTP bạn vừa nhập không chính xác hoặc đã hết hạn. Hãy thử lại!", HttpStatus.BAD_REQUEST),
    OTP_VERIFY_LIMIT("4005", "Bạn đã nhập sai OTP 3 lần vui lòng đợt 5p sau và đăng ký lại", HttpStatus.BAD_REQUEST),
    OTP_SEND_LIMIT("4006", "Bạn đã yêu cầu gửi lại OTP 3 lần vui lòng đợt 5p sau và đăng ký lại",
            HttpStatus.BAD_REQUEST),

    LOGIN_FAILED("4007", "Tên đăng nhập hoặc mật khẩu không chính xác!", HttpStatus.BAD_REQUEST),
    VERIFY_TOKEN_MISSING("4008", "Phiên xác thực OTP đã hết hạn. Vui lòng yêu cầu gửi lại mã OTP.",
            HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_MISSING("4009", "Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.", HttpStatus.UNAUTHORIZED),

    PASSWORD_REQUIRED("4010", "Mật khẩu mới là bắt buộc khi xác nhận OTP đặt lại mật khẩu", HttpStatus.BAD_REQUEST),
    PASSWORD_INVALID("4011", "Mật khẩu phải từ 8–32 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt",
            HttpStatus.BAD_REQUEST),

    PASSWORD_DUPLICATED("4012", "Mật khẩu mới không được trùng với mật khẩu cũ", HttpStatus.BAD_REQUEST),
    PASSWORD_INCORRECT("4013", "Mật khẩu không chính xác", HttpStatus.BAD_REQUEST),
    PUBLISH_FAILED("4012", "Không thể gửi sự kiện. Vui lòng thử lại sau.", HttpStatus.INTERNAL_SERVER_ERROR),

    FILM_TITLE_EXISTED("4101", "Phim có cùng tên và năm phát hành đã tồn tại", HttpStatus.CONFLICT),
    FILM_NOT_FOUND("4102", "Phim không tìm thấy", HttpStatus.NOT_FOUND),
    FILM_ERROR("4103", "Lỗi xử lý dữ liệu phim. Vui lòng liên hệ quản trị viên.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}