# Nhật ký thay đổi quyền truy cập Hall và Cinema

Ngày: 2026-05-24

## Những gì đã thay đổi

### Hall service
- Tối giản `deleteHall` chỉ còn cần `hallId`.
- Khi xoá, service tự load hall, suy ra `cinemaId`, rồi kiểm tra quyền ở phía server.
- `getHallById` giờ phụ thuộc role:
  - `ADMIN` đọc được mọi hall.
  - `MANAGER` chỉ đọc được hall thuộc rạp mình quản lý.
  - `STAFF` chỉ đọc được hall thuộc rạp mình được gán.
- `searchHalls` cũng áp dụng cùng rule scope theo role.
- Phần enrich response vẫn thực hiện sau khi đã kiểm tra quyền.

### Cinema service
- Bỏ giả định cũ rằng một manager chỉ có một cinema.
- `GET /api/cinemas/me` giờ trả về danh sách cinema của manager đang đăng nhập.
- `getCinemaById` và `searchCinemas` đều áp dụng scope theo role.
- `GET /api/cinemas/{id}/staffs` cũng kiểm tra role và ownership trước khi trả dữ liệu.

## Ý nghĩa nghiệp vụ
- Hall không còn cần client gửi `cinemaId` khi xoá.
- Quyền sở hữu được suy ra từ `hall.cinemaId -> cinema.managerId`.
- Nếu cinema đổi manager, view quyền của hall sẽ tự đổi theo ở request tiếp theo.

## Contract nội bộ
- Thêm luồng lookup danh sách cinema theo user để phục vụ ownership:
  - `GetCinemasByUserId`
  - `GetCinemasByUserIdReply`
- Luồng lookup một cinema vẫn giữ lại để tương thích ngược.

## Xác minh
- Đã rà code path và test coverage cho rule ownership mới.
- Build Maven full trong môi trường này vẫn bị chặn bởi lỗi generated-source cũ ở `common-lib`, không liên quan tới logic hall/cinema.

## File chính đã sửa
- `cinema-service/src/main/java/com/cinema/cinema_service/services/impl/CinemaServiceImpl.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/controller/CinemaController.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/services/CinemaService.java`
- `hall-service/src/main/java/com/cinema/hall_service/services/impl/HallServiceImpl.java`
- `hall-service/src/main/java/com/cinema/hall_service/controller/HallController.java`
- `hall-service/src/main/java/com/cinema/hall_service/services/HallService.java`
- `common-lib/src/main/proto/cinema_internal.proto`
