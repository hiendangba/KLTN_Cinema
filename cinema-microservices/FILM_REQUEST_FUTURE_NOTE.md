# Ghi chú mở rộng Film Request

## Mục tiêu
Ghi lại hướng mở rộng sau này cho trường hợp manager muốn đề xuất phim chưa có trong catalog.

## Rule hiện tại
- `film-service` giữ rule `ADMIN only` cho tạo, sửa, xóa phim.
- Bảng `film` chỉ chứa phim đã duyệt và được dùng cho search, showtime, booking.
- Không đổi rule hiện tại trong phase này.

## Ý tưởng mở rộng sau này
- Tạo bảng riêng `film_request` hoặc `film_proposal`.
- Manager tạo request với các thông tin cơ bản như `title`, `releaseDate`, `poster`, `genre`, `note`, `requestedBy`.
- Trạng thái request: `PENDING`, `APPROVED`, `REJECTED`.
- Admin duyệt request.
- Khi request được `APPROVED`, hệ thống tạo record chính thức trong `film`.
- Khi `REJECTED`, lưu lý do từ chối để audit.

## Vì sao nên tách bảng
- `film` luôn sạch và chỉ chứa dữ liệu chính thức.
- Dữ liệu chờ duyệt không làm rối catalog public.
- Dễ audit, tìm lịch sử duyệt, và ghi nhận lý do reject.
- Dễ mở rộng sau này nếu cần import film từ nguồn ngoài.

## Flow đề xuất sau này
1. Manager gửi request phim mới.
2. Admin xem và duyệt/từ chối.
3. Nếu duyệt, service tạo film chính thức.
4. Showtime chỉ cho phép chọn phim đã tồn tại trong `film`.

## API đề xuất sau này
- `POST /api/film-requests`
- `GET /api/film-requests/me`
- `GET /api/film-requests/{id}`
- `PATCH /api/film-requests/{id}/approve`
- `PATCH /api/film-requests/{id}/reject`

## Ghi chú
- File này chỉ là note roadmap.
- Không cần implement ngay.
- Khi cần làm feature này, đọc file này trước rồi mới mở rộng schema và API.
