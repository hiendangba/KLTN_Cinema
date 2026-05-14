# Auth Header Duplication Audit

Date: 2026-05-13

## Scope
- Quét toàn bộ các module `*-service` trong monorepo.
- Tập trung các logic lặp về:
  - Đọc `X-User-Role`, `X-User-ID`
  - Validate role (`ADMIN/MANAGER/STAFF/CUSTOMER`)
  - Parse `UUID` từ `X-User-ID`
  - Throw `BusinessException` (`FORBIDDEN`, `UNAUTHORIZED`, `INVALID_FORMAT`)

## Summary Findings
- `11` file Java đang đọc trực tiếp `X_USER_ROLE` hoặc `X_USER_ID`.
- `7` file có private role-validator kiểu `validate...Role(...)`.
- `8` file có logic parse hoặc validate `X_USER_ID`.

Các service chính có duplication rõ:
- `booking-service`
- `cinema-service`
- `film-service`
- `hall-service`
- `showtime-service`
- `user-service`
- `identity-service` (một phần)

## Current State In Common Lib
- `common-lib` đã có:
  - `HeaderNames` constants: `X_USER_ID`, `X_USER_ROLE`, `ROLE_*`.
  - `BusinessException`, `ErrorCode`.
- `common-lib` cũng đã phụ thuộc `spring-web` và có `jakarta.servlet-api` (scope `provided`), nên có thể chứa utility xử lý `HttpServletRequest`.

## Should This Go Into `common-lib`?
Kết luận: **Có**, nhưng nên tách theo 2 lớp để tránh over-coupling.

### Nên đưa vào `common-lib`
- Utility primitives dùng chung:
  - `getRequiredHeader(request, headerName, missingErrorCode)`
  - `parseRequiredUuidHeader(request, headerName)`
  - `requireRole(role, expectedRole, deniedErrorCode)`
  - `requireAnyRole(role, allowedRoles, deniedErrorCode)`
- Các hàm này giữ behavior chuẩn, giảm copy-paste, giảm lỗi khi đổi policy.

### Không nên đưa toàn bộ “business role policy” vào `common-lib`
- Một số service hiện có log format và message riêng theo action.
- Một số flow dùng rule đặc thù (`ADMIN|MANAGER`, `MANAGER only`, `CUSTOMER only`).
- Mapping lỗi có điểm khác nhau theo bối cảnh (đặc biệt flow gRPC resolve cinema/user).

=> `common-lib` nên cung cấp **building blocks**, còn policy cuối cùng vẫn nằm ở từng service.

## Recommended Refactor Strategy

### Phase 1 (Safe, low risk)
- Tạo `common-lib` util class, ví dụ:
  - `com.cinema.security.RequestAuthUtils`
- Chỉ migrate phần:
  - đọc header
  - validate missing/blank
  - parse UUID
  - check role/any-role
- Không đổi API contract và không đổi error semantics hiện tại.

### Phase 2 (Standardize)
- Chuẩn hóa helper per service:
  - `requireAdmin(...)`, `requireManager(...)`, `requireOperator(...)`
- Chuẩn hóa message/log template để observability đồng đều.

### Phase 3 (Optional)
- Nếu cần, thêm interceptor/filter ở gateway layer để giảm kiểm tra lặp ở service layer.

## Risks To Watch
- Nếu migrate ồ ạt có thể vô tình đổi semantics:
  - `FORBIDDEN` vs `UNAUTHORIZED`
  - missing header behavior
- Các test hiện tại có thể chưa bắt hết edge cases role/header.

Khuyến nghị:
- Migrate từng service theo batch nhỏ.
- Mỗi batch chạy compile + test của service đó.

## Candidate Files With Duplication (high level)
- `booking-service/.../BookingServiceImpl.java`
- `booking-service/.../ProductServiceImpl.java`
- `cinema-service/.../CinemaServiceImpl.java`
- `film-service/.../FilmServiceImpl.java`
- `hall-service/.../HallServiceImpl.java`
- `showtime-service/.../ShowTimeServiceImpl.java`
- `showtime-service/.../PricingPolicyServiceImpl.java`
- `user-service/.../UserServiceImpl.java`
- `identity-service/.../UserServiceImpl.java`

