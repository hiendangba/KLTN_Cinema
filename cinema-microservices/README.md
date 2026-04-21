# CinemaStar - Nền Tảng Đặt Vé Xem Phim Trực Tuyến

CinemaStar giúp người dùng xem lịch chiếu, chọn ghế, đặt vé và nhận thông báo nhanh chóng trong một hệ thống thống nhất.

## Mục Lục

1. [CinemaStar Dành Cho Ai?](#cinemastar-dành-cho-ai)
2. [CinemaStar Giải Quyết Vấn Đề Gì?](#cinemastar-giải-quyết-vấn-đề-gì)
3. [Bạn Có Thể Làm Gì Với CinemaStar?](#bạn-có-thể-làm-gì-với-cinemastar)
4. [Tính Năng Theo Từng Vai Trò](#tính-năng-theo-từng-vai-trò)
5. [Trải Nghiệm Đặt Vé Điển Hình](#trải-nghiệm-đặt-vé-điển-hình)
6. [Điểm Mạnh Người Dùng Nhận Được](#điểm-mạnh-người-dùng-nhận-được)
7. [Dùng Thử Nhanh (QA/Tester)](#dùng-thử-nhanh-qatester)
8. [Các Nhóm API Chính](#các-nhóm-api-chính)
9. [Câu Hỏi Thường Gặp](#câu-hỏi-thường-gặp)
10. [Xử Lý Sự Cố Nhanh](#xử-lý-sự-cố-nhanh)
11. [Hỗ Trợ](#hỗ-trợ)
12. [Tài Liệu Kỹ Thuật](#tài-liệu-kỹ-thuật)

## CinemaStar Dành Cho Ai?

- Khách hàng muốn đặt vé online nhanh, không phải xếp hàng.
- Quản lý rạp muốn quản lý phim, phòng chiếu, lịch chiếu và giá vé tập trung.
- Nhân viên rạp cần thao tác vận hành hằng ngày trên cùng một hệ thống.
- Đội QA/UAT cần môi trường test ổn định để kiểm thử luồng nghiệp vụ.

## CinemaStar Giải Quyết Vấn Đề Gì?

- Đặt vé thủ công dễ sai sót và khó kiểm soát số ghế trống theo thời gian thực.
- Quản lý lịch chiếu bằng file rời khiến khó phối hợp giữa các bộ phận.
- Thiếu hệ thống thông báo đồng bộ làm khách hàng bỏ lỡ thông tin quan trọng.
- Phân quyền không rõ ràng có thể gây rủi ro vận hành.

CinemaStar tập trung giải quyết các điểm này bằng một nền tảng tập trung, rõ trách nhiệm và dễ mở rộng.

## Bạn Có Thể Làm Gì Với CinemaStar?

- Đăng ký, đăng nhập an toàn bằng email và OTP.
- Xem danh sách phim và lịch chiếu theo từng rạp.
- Quản lý phòng chiếu và sơ đồ ghế trực quan.
- Thiết lập chính sách giá vé theo từng rạp.
- Nhận email thông báo tự động cho các sự kiện quan trọng.
- Dùng bộ API nhất quán để tích hợp với web/app frontend.

## Tính Năng Theo Từng Vai Trò

### 1. Khách hàng

- Đăng ký tài khoản và xác thực OTP.
- Đăng nhập, làm mới phiên đăng nhập và đăng xuất an toàn.
- Xem phim, lịch chiếu, thông tin suất chiếu.

### 2. Quản lý rạp

- Quản lý danh sách phòng chiếu.
- Cập nhật bố cục ghế và trạng thái phòng chiếu.
- Quản lý chính sách giá vé theo ngữ cảnh rạp đang phụ trách.

### 3. Nhân viên vận hành

- Sử dụng thông tin lịch chiếu và trạng thái phòng để hỗ trợ vận hành.
- Phối hợp với quản lý trong các tình huống thay đổi lịch hoặc bảo trì.

### 4. QA/UAT

- Kiểm thử luồng đăng nhập/xác thực.
- Kiểm thử CRUD cho phim, suất chiếu, phòng chiếu.
- Kiểm thử rule nghiệp vụ (xung đột lịch, phạm vi dữ liệu theo cinema, phân quyền).

## Trải Nghiệm Đặt Vé Điển Hình

1. Người dùng đăng nhập tài khoản.
2. Chọn phim theo nhu cầu và thời gian phù hợp.
3. Chọn rạp, suất chiếu và vị trí ghế.
4. Xác nhận thông tin đặt chỗ.
5. Nhận phản hồi kết quả và thông báo tương ứng.

## Điểm Mạnh Người Dùng Nhận Được

- Nhanh: dữ liệu được xử lý theo kiến trúc dịch vụ chuyên biệt, phản hồi ổn định.
- An toàn: xác thực tập trung, phân quyền theo vai trò.
- Rõ ràng: lỗi nghiệp vụ được trả về nhất quán, dễ hiểu.
- Linh hoạt: có thể mở rộng thêm payment, khuyến mãi, loyalty trong tương lai.
- Dễ tích hợp: API được tổ chức theo nhóm nghiệp vụ rõ ràng.

## Tình Trạng Dự Án

- Đây là backend microservices cho hệ thống CinemaStar.
- Frontend không nằm trong repository này.
- Trạng thái hiện tại: phù hợp cho môi trường dev/test/UAT.
- Thời điểm cập nhật README: 21/04/2026.

## Dùng Thử Nhanh (QA/Tester)

### Cách 1: Chạy toàn bộ bằng Docker

```bash
docker compose -f compose.prod.yaml up -d
```

### Cách 2: Chế độ dev (service chạy local, gateway chạy Docker)

```bash
docker compose -f compose.prod.yaml -f compose.local.yaml up -d
```

Sau khi hệ thống chạy, API Gateway mặc định qua cổng `80`.

### Lệnh thường dùng khi test

```bash
docker compose -f compose.prod.yaml ps
```

```bash
docker compose -f compose.prod.yaml logs -f showtime-service
```

```bash
docker compose -f compose.prod.yaml down -v
```

## Các Nhóm API Chính

- `Identity`: đăng ký, đăng nhập, OTP, refresh token.
- `User`: thông tin hồ sơ người dùng.
- `Film`: quản lý phim và tìm kiếm phim.
- `Showtime`: quản lý suất chiếu và chính sách giá.
- `Hall`: quản lý phòng chiếu và sơ đồ ghế.
- `Email`: gửi thông báo bất đồng bộ.

## Câu Hỏi Thường Gặp

### 1. Tôi là người dùng cuối, có cần hiểu microservices không?
Không. Bạn chỉ cần dùng ứng dụng/web tích hợp CinemaStar, hệ thống backend đã xử lý phần kỹ thuật phía sau.

### 2. Vì sao có nhiều service?
Để hệ thống ổn định hơn khi tải cao, tách rõ trách nhiệm và dễ nâng cấp từng phần mà không ảnh hưởng toàn bộ.

### 3. Nếu không nhận được OTP thì sao?
Kiểm tra email spam/rác, sau đó gửi lại OTP. Hệ thống có giới hạn số lần gửi để bảo mật.

### 4. Vì sao có lúc API trả về lỗi business dù request đúng format?
Do request có thể vi phạm rule nghiệp vụ thực tế, ví dụ xung đột lịch chiếu hoặc dữ liệu không thuộc phạm vi rạp hiện tại.

### 5. Có thể dùng backend này cho mobile app không?
Có. Backend cung cấp API theo domain và có thể dùng cho web/mobile tùy frontend tích hợp.

## Xử Lý Sự Cố Nhanh

### 1. Gọi API qua gateway bị lỗi 404

- Kiểm tra container `envoy` đang chạy.
- Kiểm tra route path đúng prefix API.
- Kiểm tra service đích có đang chạy hay không.

### 2. Đăng nhập được nhưng API nghiệp vụ báo thiếu quyền

- Kiểm tra role tài khoản hiện tại.
- Kiểm tra header nội bộ được gateway forward đúng.
- Kiểm tra endpoint đó yêu cầu role gì.

### 3. Gửi OTP chậm hoặc không nhận được

- Kiểm tra cấu hình email service.
- Kiểm tra hàng đợi RabbitMQ.
- Kiểm tra log `email-service`.

### 4. Lỗi liên quan dữ liệu phòng chiếu/sơ đồ ghế

- Kiểm tra payload `layoutJson` đúng schema.
- Kiểm tra trạng thái phòng chiếu có đang bảo trì không.
- Kiểm tra rule trùng tên phòng trong cùng cinema.

### 5. Lỗi UTF-8/Mojibake (chuỗi hiển thị thành `MÃ£`, `Ã„`, ...)

- Nguyên nhân thường gặp: file Java bị lưu sai encoding hoặc bị chuyển mã nhiều lần.
- Chuẩn bắt buộc: lưu source dưới dạng `UTF-8` (không BOM).
- Với chuỗi tiếng Việt quan trọng cho UI/email, có thể dùng Unicode escape Java (`\uXXXX`) để tránh lỗi môi trường.
- Khi nghi ngờ file lỗi mã hóa, kiểm tra nhanh bằng cách tìm pattern bất thường như `MÃ`, `Ã„`, `Ã`.
- Sau khi sửa, luôn chạy compile để xác nhận:

```bash
mvn -pl identity-service -am -DskipTests test-compile
```

## Hỗ Trợ

Nếu bạn là người dùng nghiệp vụ (vận hành rạp/QA/UAT), hãy gửi thông tin sau khi báo lỗi:

- Thời điểm xảy ra lỗi.
- API hoặc màn hình đang thao tác.
- Request mẫu (ẩn dữ liệu nhạy cảm).
- Mã lỗi và nội dung thông báo trả về.
- Log liên quan (nếu có).

## Tài Liệu Kỹ Thuật

Nếu bạn là developer hoặc AI agent cần tài liệu kỹ thuật chi tiết hơn, xem tại:

- `TECHNICAL_AGENT_GUIDE.md`

---

## Nhật Ký Skill AI

- Quy ước: sau mỗi lần Codex chỉnh sửa code, cần nêu rõ tên skill đã dùng trong phần báo cáo.
- 11/04/2026:
  - Skills: `java-pro`, `backend-dev-guidelines`, `api-documentation`
  - Phạm vi áp dụng: `common-lib`, `hall-service`, `film-service`, `showtime-service`, `user-service`, `compose/pom`
  - Skills: `java-pro`, `backend-dev-guidelines`
  - Phạm vi áp dụng: sửa lỗi generic Criteria API (`Path<Comparable>`) tại `film-service`, đồng bộ `hall-service`, `showtime-service`
- 14/04/2026:
  - Skills: `architecture`
  - Phạm vi áp dụng: chuẩn hóa cấu hình production không dùng `env_file`, khai báo biến tập trung qua `environment` cho toàn bộ services trong `compose.prod.yaml`
- 17/04/2026:
  - Skills: `backend-dev-guidelines`
  - Phạm vi áp dụng: sửa lỗi UTF-8/mojibake và chuẩn hóa comment theo từng khối xử lý tại `identity-service/services/impl/UserServiceImpl.java`, bổ sung hướng dẫn xử lý encoding trong `README.md`
- 21/04/2026:
  - Skills: `architect-review`, `backend-dev-guidelines`, `api-documentation`
  - Phạm vi áp dụng: rà soát toàn bộ repo, đồng bộ lại lệnh chạy/build trong `README.md` và `TECHNICAL_AGENT_GUIDE.md` theo `compose.prod.yaml`/`compose.local.yaml`, cập nhật mapping cấu trúc Envoy/Compose/DB, dọn log debug `System.out.println` trong `identity-service/services/impl/UserServiceImpl.java`
  - Skills: `backend-dev-guidelines`, `api-documentation`, `architect-review`
  - Phạm vi áp dụng: bổ sung API `GET /api/users/me` tại `user-service` theo đúng cấu trúc `controller -> service -> repository`, cập nhật docs endpoint trong `TECHNICAL_AGENT_GUIDE.md`

---

## Phụ Lục Thuật Ngữ

- OTP: Mã xác thực dùng một lần.
- API Gateway: lớp cổng vào, định tuyến và kiểm tra truy cập trước khi vào service đích.
- gRPC: giao thức gọi nội bộ giữa các service.
- Microservices: kiến trúc tách hệ thống thành nhiều dịch vụ nhỏ theo domain.
- UAT: kiểm thử chấp nhận người dùng trước khi triển khai thực tế.
