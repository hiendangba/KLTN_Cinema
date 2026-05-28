# Kiểm toán trùng lặp header auth

Ngày: 2026-05-13

## Phạm vi
- Quét toàn bộ các module `*-service` trong monorepo.
- Tập trung vào logic lặp quanh:
  - Đọc `X-User-Role`, `X-User-ID`
  - Kiểm tra role (`ADMIN`, `MANAGER`, `STAFF`, `CUSTOMER`)
  - Parse `UUID` từ `X-User-ID`
  - Throw `BusinessException` với các lỗi `FORBIDDEN`, `UNAUTHORIZED`, `INVALID_FORMAT`

## Kết quả chính
- Có nhiều file Java đang đọc trực tiếp header auth giống nhau.
- Có nhiều file tự viết hàm kiểm tra role riêng.
- Có nhiều file tự parse hoặc validate `X-User-ID` riêng.

Các service có duplication rõ:
- `booking-service`
- `cinema-service`
- `film-service`
- `hall-service`
- `showtime-service`
- `user-service`
- `identity-service` (một phần)

## Trạng thái hiện tại trong `common-lib`
- `common-lib` đã có:
  - `HeaderNames` với `X_USER_ID`, `X_USER_ROLE`, `ROLE_*`.
  - `BusinessException`, `ErrorCode`.
- `common-lib` cũng đã phụ thuộc `spring-web` và có `jakarta.servlet-api` ở scope `provided`, nên có thể chứa util xử lý `HttpServletRequest`.

## Có nên đưa vào `common-lib` không?
Kết luận: **có**, nhưng chỉ nên đưa phần primitive dùng chung, không đưa toàn bộ policy nghiệp vụ vào đó.

### Nên đưa vào `common-lib`
- Các util chung như:
  - `getRequiredHeader(request, headerName, missingErrorCode)`
  - `parseRequiredUuidHeader(request, headerName)`
  - `requireRole(role, expectedRole, deniedErrorCode)`
  - `requireAnyRole(role, allowedRoles, deniedErrorCode)`
- Mục tiêu là giảm copy-paste và giảm lỗi khi đổi policy.

### Không nên đưa toàn bộ policy role vào `common-lib`
- Mỗi service vẫn có message và log riêng theo nghiệp vụ.
- Một số flow có rule đặc thù (`ADMIN|MANAGER`, `MANAGER only`, `CUSTOMER only`).
- Một số lỗi phụ thuộc ngữ cảnh gRPC resolve cinema/user.

=> `common-lib` nên cung cấp **building blocks**, còn policy cuối cùng vẫn nằm ở từng service.

## Chiến lược refactor đề xuất

### Giai đoạn 1
- Tạo util dùng chung trong `common-lib`, ví dụ:
  - `com.cinema.security.RequestAuthUtils`
- Chỉ migrate phần:
  - đọc header
  - kiểm tra thiếu/blank
  - parse UUID
  - kiểm tra role/any-role
- Không đổi contract API và không đổi semantics lỗi hiện tại.

### Giai đoạn 2
- Chuẩn hoá helper per service:
  - `requireAdmin(...)`, `requireManager(...)`, `requireOperator(...)`
- Chuẩn hoá format log/message để dễ observability.

### Giai đoạn 3
- Nếu cần, thêm interceptor/filter ở gateway layer để giảm việc kiểm tra lặp ở service layer.

## Rủi ro cần theo dõi
- Nếu migrate quá ồ ạt có thể vô tình đổi semantics:
  - `FORBIDDEN` vs `UNAUTHORIZED`
  - hành vi khi thiếu header
- Test hiện tại có thể chưa bao hết edge case role/header.

Khuyến nghị:
- Migrate từng service theo batch nhỏ.
- Mỗi batch phải compile và test chính service đó.

## Các file còn lặp theo nhóm lớn
- `booking-service/.../BookingServiceImpl.java`
- `booking-service/.../ProductServiceImpl.java`
- `cinema-service/.../CinemaServiceImpl.java`
- `film-service/.../FilmServiceImpl.java`
- `hall-service/.../HallServiceImpl.java`
- `showtime-service/.../ShowTimeServiceImpl.java`
- `showtime-service/.../PricingPolicyServiceImpl.java`
- `user-service/.../UserServiceImpl.java`
- `identity-service/.../UserServiceImpl.java`
