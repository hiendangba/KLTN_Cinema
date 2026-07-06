# Minh chứng kiểm thử API CinemaStar bằng Postman/Newman

## Thông tin chạy kiểm thử

| Hạng mục | Giá trị |
|---|---|
| Công cụ | Postman Collection + Newman CLI |
| Collection | `CinemaStar Production Smoke Tests` |
| Environment | `CinemaStar Production` |
| Môi trường | `https://cinema-api.duckdns.org` |
| Folder kiểm thử | `ReadOnly Smoke` |
| Thời gian xuất report | `06/07/2026 01:14:35` |
| Tổng request | `19` |
| Tổng assertion | `57` |
| Tổng lỗi | `0` |
| Thời gian chạy | `10.6s` |
| Thời gian phản hồi trung bình | `317ms` |
| Dữ liệu nhận về | `139.8KB` |

## File minh chứng

| File | Ý nghĩa |
|---|---|
| `tools/postman/reports/report.html` | Báo cáo HTML trực quan để chụp màn hình đưa vào luận văn |
| `tools/postman/reports/junit.xml` | Báo cáo JUnit XML dùng làm bằng chứng máy chạy tự động |
| `tools/postman/CinemaStar.postman_collection.json` | Kịch bản kiểm thử API |
| `tools/postman/cinema-prod.postman_environment.json` | Biến môi trường production dùng khi chạy test |

## Kết quả chi tiết theo API

| STT | Nhóm kiểm thử | API chính | Mục tiêu kiểm thử | Assertions | Lỗi | Thời gian |
|---:|---|---|---|---:|---:|---:|
| 1 | Auth | `POST /api/auth/login` | Đăng nhập admin và nhận cookie xác thực | 4 | 0 | 0.488s |
| 2 | Film | `POST /api/films/search` | Tìm kiếm phim, bắt `filmId`, kiểm tra `averageRating` và `reviewCount` | 4 | 0 | 0.137s |
| 3 | Film | `GET /api/films/{filmId}` | Lấy chi tiết phim từ `filmId` thật | 4 | 0 | 0.044s |
| 4 | Showtime | `POST /api/showtimes/search` | Tìm kiếm suất chiếu, bắt `showtimeId`, `cinemaId`, `hallId` | 4 | 0 | 0.974s |
| 5 | Showtime | `GET /api/showtimes/{showtimeId}` | Lấy chi tiết suất chiếu | 3 | 0 | 0.068s |
| 6 | Showtime | `GET /api/showtimes/{showtimeId}/seat-map` | Kiểm tra sơ đồ ghế của suất chiếu | 3 | 0 | 0.050s |
| 7 | Showtime | `POST /api/showtimes/films/{filmId}/search` | Tìm suất chiếu theo phim và ngày chiếu thật | 3 | 0 | 0.203s |
| 8 | Review | `POST /api/films/{filmId}/reviews/search` | Tìm kiếm đánh giá theo phim | 3 | 0 | 0.045s |
| 9 | Payment Report | `POST /api/payments/revenues/cinemas/search` | Báo cáo doanh thu theo rạp cho admin | 3 | 0 | 0.169s |
| 10 | Payment Report | `POST /api/payments/revenues/films/search` | Báo cáo doanh thu theo phim, kiểm tra `filmName` không null khi có dữ liệu | 3 | 0 | 0.119s |
| 11 | Booking Report | `POST /api/bookings/reports/showtimes/search` | Báo cáo hiệu suất suất chiếu, kiểm tra tên rạp/phòng/phim và tỷ lệ lấp ghế | 3 | 0 | 1.113s |
| 12 | Export Excel | `POST /api/payments/revenues/cinemas/export` | Kiểm tra export Excel doanh thu theo rạp trả file không rỗng | 2 | 0 | 0.193s |
| 13 | Export Excel | `POST /api/bookings/reports/showtimes/export` | Kiểm tra export Excel hiệu suất suất chiếu trả file không rỗng | 2 | 0 | 1.800s |
| 14 | Auth | `POST /api/auth/login` | Đăng nhập manager | 2 | 0 | 0.157s |
| 15 | Payment Report | `POST /api/payments/revenues/cinemas/me/search` | Báo cáo doanh thu theo rạp trong phạm vi manager | 3 | 0 | 0.113s |
| 16 | Auth | `POST /api/auth/login` | Đăng nhập customer | 2 | 0 | 0.142s |
| 17 | Booking | `POST /api/bookings/me/history/search` | Lấy lịch sử đặt vé của customer | 3 | 0 | 0.131s |
| 18 | Booking | `POST /api/bookings/me/active/search` | Lấy booking đang hoạt động của customer | 3 | 0 | 0.030s |
| 19 | Payment | `GET /api/payments/sessions/{bookingId}` | Lấy phiên thanh toán theo booking thật nếu có | 3 | 0 | 0.057s |

## Dữ liệu thật được dùng trong lần chạy

Newman không tạo phim mới. Bộ kiểm thử gọi `POST /api/films/search` để lấy phim có sẵn trên production rồi dùng `filmId` đó cho các API tiếp theo.

Ví dụ dữ liệu bắt được trong report:

| Field | Giá trị |
|---|---|
| `filmId` | `019ea2aa-34d2-77aa-be93-9cad2c1d9cf2` |
| `title` | `MA XÓ` |
| `timeCreated` | `2026-06-07T22:18:44` |
| `timeUpdated` | `2026-07-04T04:27:32` |
| `showtimeId` | `019e91d9-192c-7cf1-9d84-d46f7bae88a9` |
| `bookingId` | `019f3168-97d2-7d0b-9074-b2705d7081bd` |

## Kết luận

Bộ kiểm thử read-only đã xác nhận các nhóm chức năng chính của hệ thống CinemaStar trên môi trường production gồm xác thực, danh mục phim, suất chiếu, sơ đồ ghế, đánh giá, booking, thanh toán, báo cáo và export Excel. Kết quả chạy Newman đạt `57/57` assertion thành công, không có request hoặc test thất bại.

## Ghi chú

- Nhóm mutation đang được khóa bằng `RUN_MUTATIONS=false`, nên lần chạy minh chứng không tạo/sửa/xóa dữ liệu production.
- Tài khoản `staff@gmail.com` đang bị production trả lỗi credential `4007`, nên smoke test staff được để optional qua biến `staffEnabled=false`.
