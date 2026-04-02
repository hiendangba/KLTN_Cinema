# Kế hoạch Triển khai: Khởi tạo và Phát triển `hall-service`

## Mục tiêu
Thiết lập và xây dựng hoàn chỉnh Microservice quản lý Phòng Chiếu (Hall) và Sơ đồ Ghế ngồi (Seat) để làm nền tảng vững chắc cho hệ thống Đặt vé (Booking Service) sắp tới. 

> **Ghi chú:** Đây là bản nháp nội bộ, chưa cần đẩy lên Github. Mình dùng để đọc và bám sát cho lần ra mắt tiếp theo.

## Các thay đổi cần thực hiện

### 1. Cấu hình Hạ tầng & Nền tảng (Infrastructure)
* Cập nhật `pom.xml` gốc để khai báo module `<module>hall-service</module>`.
* Khởi tạo thư mục `hall-service` chứa cấu trúc chuẩn của Spring Boot.
* Bổ sung `hall-service` vào `compose.yaml` (cho môi trường Docker).
* Cấp cổng độc lập cho Service này. Đề xuất sử dụng Port **`8086`** (Vì 8081-8085 đã được các khối khác sử dụng).

### 2. Mô hình Dữ liệu (Entity & Database)
Tạo 2 bảng chiến lược sau trong sơ đồ JPA (nên tạo Schema DB mới là `cinema_hall`):

**Entity `Hall` (Phòng chiếu):**
* `id` (UUID - Khóa chính)
* `name` (String - Tên phòng chiếu: "Phòng 1", "IMAX")
* `totalSeats` (Integer - Tổng cộng sức chứa)
* `status` (Enum - Trạng thái: `ACTIVE`, `MAINTENANCE`)
* Kế thừa `BaseEntity` (timeCreated, timeUpdated).

**Entity `Seat` (Ghế ngồi):**
* `id` (UUID - Khóa chính)
* `hallId` (UUID - Thuộc về phòng nào)
* `seatRow` (String - Dãy ghế: 'A', 'B', 'C')
* `seatColumn` (Integer - Số ghế: 1, 2, 3)
* `type` (Enum - Loại ghế: `STANDARD`, `VIP`, `COUPLE`)
* `isDeleted` (Boolean - Đã gỡ bỏ hay chưa, dùng cho soft delete)

### 3. Tầng Dịch vụ (Service Layer) & API
Xây dựng tập hợp API khép kín cho `HallController`:

* `POST /api/halls`: Tạo mới phòng chiếu. Logic: Khi nhập `rowCount` (số hàng) và `colCount` (số cột), hệ thống sẽ chạy vòng lặp sinh ra mọi tọa độ ghế và cho vào DB.
* `GET /api/halls/{id}`: Lấy thông tin phòng chiếu (kèm theo cả mảng Layout của tất cả Ghế đi kèm).
* `POST /api/halls/search`: API tìm kiếm có phân trang Cursor (Tương tự hệ thống hiện tại).
* `PATCH /api/halls/{id}/seats`: Cập nhật trạng thái một danh sách ghế (Thăng bậc từ Standard lên VIP).

### 4. Bổ sung `common-lib`
* Cập nhật thêm các `ErrorCode` cho lĩnh vực Rạp chiếu nếu chưa có (VD: `SEAT_NOT_FOUND`, `HALL_MAINTENANCE`).
