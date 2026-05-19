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
        INVALID_INPUT("4000", "Dữ liệu đầu vào không hợp lệ", HttpStatus.BAD_REQUEST),
        NOT_CREATED("4000", "Không thể tạo thành công, vui lòng liên hệ quản trị viên để được hỗ trợ",
                        HttpStatus.BAD_REQUEST),
        ID_EXISTED("4000", "Lỗi trong lúc tạo mới, vui lòng thử lại vì ID đã trùng!", HttpStatus.CONFLICT),
        EMAIL_EXISTED("4001", "Email đã được đăng ký trước đó", HttpStatus.CONFLICT),
        USER_NOT_FOUND("4002", "Không tìm thấy người dùng tương ứng", HttpStatus.NOT_FOUND),
        OTP_ALREADY_SENT("4003", "Đã gửi mã OTP rồi. Vui lòng đợi 5 phút sau thử lại!", HttpStatus.CONFLICT),
        OTP_INVALID("4004", "Mã OTP bạn vừa nhập không chính xác hoặc đã hết hạn. Hãy thử lại!",
                        HttpStatus.BAD_REQUEST),
        OTP_VERIFY_LIMIT("4005", "Bạn đã nhập sai OTP 3 lần, vui lòng đợi 5 phút sau và đăng ký lại",
                        HttpStatus.BAD_REQUEST),
        OTP_SEND_LIMIT("4006", "Bạn đã yêu cầu gửi lại OTP 3 lần, vui lòng đợi 5 phút sau và đăng ký lại",
                        HttpStatus.BAD_REQUEST),

        LOGIN_FAILED("4007", "Tên đăng nhập hoặc mật khẩu không chính xác!", HttpStatus.BAD_REQUEST),
        VERIFY_TOKEN_MISSING("4008", "Phiên xác thực OTP đã hết hạn. Vui lòng yêu cầu gửi lại mã OTP.",
                        HttpStatus.UNAUTHORIZED),
        REFRESH_TOKEN_MISSING("4009", "Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.", HttpStatus.UNAUTHORIZED),

        PASSWORD_REQUIRED("4010", "Mật khẩu mới là bắt buộc khi xác nhận OTP đặt lại mật khẩu", HttpStatus.BAD_REQUEST),
        PASSWORD_INVALID("4011", "Mật khẩu phải từ 8-32 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt",
                        HttpStatus.BAD_REQUEST),

        PASSWORD_DUPLICATED("4012", "Mật khẩu mới không được trùng với mật khẩu cũ", HttpStatus.BAD_REQUEST),
        PASSWORD_INCORRECT("4013", "Mật khẩu không chính xác", HttpStatus.BAD_REQUEST),
        PUBLISH_FAILED("4012", "Không thể gửi sự kiện. Vui lòng thử lại sau.", HttpStatus.INTERNAL_SERVER_ERROR),

        FILM_TITLE_EXISTED("4101", "Phim có cùng tên và năm phát hành đã tồn tại", HttpStatus.CONFLICT),
        FILM_NOT_FOUND("4102", "Phim không tìm thấy", HttpStatus.NOT_FOUND),
        FILM_ERROR("4103", "Lỗi xử lý dữ liệu phim. Vui lòng liên hệ quản trị viên.", HttpStatus.INTERNAL_SERVER_ERROR),

        INVALID_END_TIME("4200", "Thời gian kết thúc không hợp lệ để tạo một suất chiếu", HttpStatus.BAD_REQUEST),
        NOT_CREATED_SHOWTIME("4201", "Không thể tạo suất chiếu. Vui lòng liên hệ quản trị viên để được hỗ trợ",
                        HttpStatus.BAD_REQUEST),
        ALL_TIME_SLOT_OCCUPIED("4202", "Tất cả các khung giờ đã có suất chiếu.", HttpStatus.BAD_REQUEST),
        SHOWTIME_NOT_FOUND("4203", "Suất chiếu không tìm thấy", HttpStatus.NOT_FOUND),
        NOT_UPDATE_BOOKED_SHOWTIME("4204", "Không thể cập nhật trạng thái của suất chiếu đã được đặt vé",
                        HttpStatus.BAD_REQUEST),
        NOT_UPDATE_CANCELLED_SHOWTIME("4205", "Không thể cập nhật trạng thái của suất chiếu đã bị hủy",
                        HttpStatus.BAD_REQUEST),
        NOT_UPDATE_FINISHED_SHOWTIME("4206", "Không thể cập nhật trạng thái của suất chiếu đã kết thúc",
                        HttpStatus.BAD_REQUEST),
        DELETED_SHOWTIME("4207", "Suất chiếu đã bị xóa trước đó rồi", HttpStatus.BAD_REQUEST),
        INVALID_PRICING_ORDER("4208", "Giá STANDARD phải nhỏ hơn giá VIP và giá VIP phải nhỏ hơn giá COUPLE",
                        HttpStatus.BAD_REQUEST),
        PRICING_POLICY_NOT_IN_CINEMA("4209", "Pricing policy không thuộc cinema hiện tại", HttpStatus.BAD_REQUEST),
        HALL_NOT_IN_CINEMA("4210", "Hall không thuộc cinema hiện tại", HttpStatus.BAD_REQUEST),

        HALL_NOT_FOUND("4301", "Phòng chiếu không tìm thấy", HttpStatus.NOT_FOUND),
        CINEMA_NOT_FOUND("4302", "Cinema không tìm thấy", HttpStatus.NOT_FOUND),
        HALL_NAME_EXISTED("4303", "Tên phòng chiếu đã tồn tại trong cinema", HttpStatus.CONFLICT),
        SEAT_NOT_FOUND("4304", "Ghế không tìm thấy trong phòng chiếu", HttpStatus.NOT_FOUND),
        HALL_MAINTENANCE("4305", "Phòng chiếu đang bảo trì", HttpStatus.BAD_REQUEST),
        MANAGER_NOT_ASSIGNED_CINEMA("4306", "Quản lý này không quản lý cinema nào cả", HttpStatus.BAD_REQUEST),

        EMAIL_SEND_FAILED("9100", "Gửi email thất bại. Vui lòng thử lại sau.", HttpStatus.INTERNAL_SERVER_ERROR),
        FILM_SERVICE_ERROR("9101", "Lỗi khi gọi Film Service. Vui lòng thử lại sau.", HttpStatus.SERVICE_UNAVAILABLE),
        HALL_SERVICE_ERROR("9102", "Lỗi khi gọi Hall Service. Vui lòng thử lại sau.", HttpStatus.SERVICE_UNAVAILABLE),
        BOOKING_TICKET_LIMIT_EXCEEDED("4401", "One booking can include at most 5 tickets.", HttpStatus.BAD_REQUEST),
        BOOKING_SERVICE_ERROR("9104", "Lỗi khi gọi Booking Service. Vui lòng thử lại sau.", HttpStatus.SERVICE_UNAVAILABLE),
        CINEMA_SERVICE_ERROR("9105", "Lỗi khi gọi Cinema Service. Vui lòng thử lại sau.", HttpStatus.SERVICE_UNAVAILABLE),
        SHOWTIME_SERVICE_ERROR("9106", "Lỗi khi gọi Showtime Service. Vui lòng thử lại sau.", HttpStatus.SERVICE_UNAVAILABLE),
        SEAT_SERVICE_ERROR("9107", "Lỗi khi gọi Seat Service. Vui lòng thử lại sau.", HttpStatus.SERVICE_UNAVAILABLE),
        UN_SUPPORTED_FIELD_TYPE("9103", "Loại dữ liệu của trường không được hỗ trợ", HttpStatus.BAD_REQUEST);

        private final String code;
        private final String message;
        private final HttpStatus httpStatus;
}


