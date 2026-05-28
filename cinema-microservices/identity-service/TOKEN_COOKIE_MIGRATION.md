# Tài liệu chuyển đổi Token sang Cookie (identity-service)

## Mục tiêu

Chuyển luồng xác thực sang mô hình ưu tiên cookie cho `accessToken`, đồng bộ với `refreshToken`, và cập nhật toàn bộ API liên quan đến token.

## Thay đổi đã áp dụng

1. `POST /api/auth/login`

- Phát hành và lưu cả `accessToken` + `refreshToken` vào cookie.
- Không còn trả `accessToken` trong response body.
- API trả về `ActionMessageResponse`.

2. `POST /api/auth/refresh_token`

- Đọc `refreshToken` từ cookie.
- Rotate token và set lại cả `accessToken` + `refreshToken` cookie.
- Không còn trả `accessToken` trong response body.
- API trả về `ActionMessageResponse`.
- Nếu refresh lỗi, hệ thống sẽ clear cả 2 cookie auth.

3. `POST /api/auth/logout`

- Đã nhận thêm `HttpServletResponse` trong service/controller để clear cookie.
- Đọc `accessToken` ưu tiên từ cookie, fallback sang `Authorization: Bearer ...`.
- Revoke token trong Redis và clear cả 2 cookie auth.

4. `JwtAuthenticationFilter`

- Đọc `accessToken` ưu tiên từ cookie `accessToken`.
- Nếu không có cookie thì fallback sang header `Authorization: Bearer ...` để tương thích ngược.

## Chính sách cookie hiện tại (token cookies)

- `HttpOnly: true`
- `Path: /`
- `Max-Age`: theo TTL của access/refresh token

Giá trị `Secure` và `SameSite` được cấu hình qua:

- `AUTH_COOKIE_SECURE` (mặc định `false`)
- `AUTH_COOKIE_SAME_SITE` (mặc định `None`)

## Unit test đã thêm

1. `UserServiceImplTokenFlowTest`

- Kiểm tra `login` set đủ 2 cookie token và lưu token vào Redis.
- Kiểm tra `logout` luôn clear cookie kể cả khi request không mang token.
- Kiểm tra `refresh_token`:
  - Thiếu cookie refresh => ném `REFRESH_TOKEN_MISSING` và clear cookie.
  - Refresh hợp lệ => rotate token, set lại cookie mới.
  - Access token cũ còn hiệu lực trong Redis => từ chối và clear cookie.

2. `JwtAuthenticationFilterTest`

- Kiểm tra filter xác thực được bằng `accessToken` trong cookie.
- Kiểm tra fallback sang header `Authorization` khi thiếu cookie.
- Kiểm tra bỏ qua xác thực khi request không mang token.

## Danh sách file đã sửa

- `identity-service/src/main/java/com/cinema/identity_service/services/UserService.java`
- `identity-service/src/main/java/com/cinema/identity_service/controller/UserController.java`
- `identity-service/src/main/java/com/cinema/identity_service/services/impl/UserServiceImpl.java`
- `identity-service/src/main/java/com/cinema/identity_service/config/JwtAuthenticationFilter.java`
- `identity-service/src/main/java/com/cinema/identity_service/dto/response/LoginResponse.java` (đã xóa)
- `identity-service/src/main/resources/application.yaml`
- `identity-service/src/test/java/com/cinema/identity_service/services/impl/UserServiceImplTokenFlowTest.java`
- `identity-service/src/test/java/com/cinema/identity_service/config/JwtAuthenticationFilterTest.java`

## Lưu ý cho frontend

1. Bật `withCredentials = true` khi gọi API.
2. Nếu trước đây frontend gửi `Authorization` header bằng access token, hệ thống vẫn hỗ trợ fallback.
3. Khi deploy production qua HTTPS, đặt `AUTH_COOKIE_SECURE=true`.

