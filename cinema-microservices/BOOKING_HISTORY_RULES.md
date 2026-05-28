# Quy tắc Lịch sử Booking

## Mục tiêu

Lịch sử booking phải tự đủ dữ liệu để hiển thị và tìm kiếm, không phụ thuộc `UserService` hay `ShowtimeService` khi đọc lại.

## Snapshot bắt buộc trong `Booking`

- `customerInfo.fullName`
- `customerInfo.email`
- `customerInfo.phone`
- `filmTitle`
- `showtimeStartDateTime`
- `showtimeEndDateTime`

## Quy tắc tạo booking

- `customerInfo` luôn bắt buộc.
- Nếu booking được tạo bởi khách đã đăng nhập, `userId` có thể được gắn để liên kết tài khoản.
- Nếu booking được tạo bởi staff cho khách vãng lai, `customerInfo` vẫn là nguồn dữ liệu chính và `userId` có thể để trống.

## Quy tắc đọc lịch sử

- `POST /api/bookings/me/search` dùng cho khách đã đăng nhập.
- `POST /api/bookings/cinemas/me/search` dùng cho operator/staff.
- Tìm theo tên khách dựa trên `customerInfo.fullName`.
- Tìm theo tên phim dựa trên `filmTitle`.
- Không cần gọi lại `UserService` hoặc `ShowtimeService` khi đọc lịch sử.

