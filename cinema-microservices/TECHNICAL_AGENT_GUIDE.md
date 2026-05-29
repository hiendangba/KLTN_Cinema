# Sổ tay kỹ thuật CinemaStar

> Tài liệu kỹ thuật chi tiết cho Developer và AI Agent.
> 
> Mục tiêu:
> - Giữ `README.md` thân thiện với end user.
> - Dồn toàn bộ kỹ thuật chuyên sâu vào file này để onboarding/maintenance nhanh hơn.

---

## Cách dùng tài liệu này

- Nếu bạn là người mới vào dự án: đọc theo thứ tự từ trên xuống.
- Nếu bạn là AI agent: ưu tiên đọc phần "Sổ tay tác nghiệp AI" ở cuối trước khi sửa code.
- Nếu bạn sửa liên service: luôn kiểm tra mục gRPC Contracts + Compose/Envoy.

---

## Nền tảng kỹ thuật cũ (chi tiết 500+ dòng)

Phần bên dưới là bản kỹ thuật chi tiết đã được dùng trong dự án trước đó, giữ lại để tham chiếu đầy đủ.
Lưu ý: một số tên file compose/envoy trong phần lịch sử có thể khác với cấu trúc hiện tại, hãy ưu tiên các phần "Chạy nhanh", "Cấu trúc dự án" và "Hướng dẫn chạy" bên dưới.
# 🎬 CinemaStar — Hệ Thống Đặt Vé Rạp Phim (Microservices)

> **Khóa luận tốt nghiệp** — Hệ thống đặt vé xem phim trực tuyến hiện đại, xây dựng trên kiến trúc Microservices tinh gọn, bảo mật và hiệu suất cao.

---

## ⚡ Chạy Nhanh (Dev/Prod)

- **Dev (service chạy local VS Code, Envoy chạy Docker):**
```bash
docker compose -f compose.prod.yaml -f compose.local.yaml up -d
```
- **Prod (tất cả chạy trong Docker):**
```bash
docker compose -f compose.prod.yaml up -d
```

## 📋 Mục Lục

1. [🚀 Giới Thiệu](#-giới-thiệu)
2. [🏗️ Kiến Trúc Hệ Thống (Architecture)](#️-kiến-trúc-hệ-thống)
3. [🛠️ Công Nghệ Sử Dụng (Tech Stack)](#️-công-nghệ-sử-dụng)
4. [📁 Cấu Trúc Dự Án (Project Structure)](#-cấu-trúc-dự-án)
5. [📦 Chi Tiết Từng Service (Services Deep-Dive)](#-chi-tiết-từng-service)
6. [🌐 API Endpoints](#-api-endpoints)
7. [🐘 Cơ Sở Dữ Liệu & Caching (DB & Cache)](#-cơ-sở-dữ-liệu--caching)
8. [🚀 Hướng Dẫn Cài Đặt (Setup)](#-hướng-dẫn-cài-đặt)
9. [▶️ Hướng Dẫn Chạy (Run)](#️-hướng-dẫn-chạy)
10. [⚙️ Cấu Hình Môi Trường (Environment)](#️-cấu-hình-môi-trường)
11. [🗺️ Lịch Sử & Lộ Trình Phát Triển (Roadmap)](#️-lịch-sử-phát-triển)

---

## 🎯 Giới Thiệu

**CinemaStar** là hệ thống quản lý và đặt vé xem phim trực tuyến, được xây dựng theo kiến trúc **Microservices**. Hệ thống không chỉ là một ứng dụng đặt vé, mà là một hệ sinh thái hoàn chỉnh được thiết kế để giải quyết các bài toán về:

- 🔐 **Đăng ký / Đăng nhập** bằng email + OTP. Quản lý xác thực tập trung tại API Gateway.
- 👤 **Quản lý thông tin người dùng** với các roles: Customer, Manager, Staff, Admin.
- 🎬 **Quản lý phim** (Kho phim, tìm kiếm, phân loại) hỗ trợ phân trang hiệu suất cao (Cursor Pagination).
- 📅 **Quản lý lịch chiếu** (Tạo suất chiếu, bảo vệ chống xung đột thời gian, tự động cập nhật trạng thái).
- 📧 **Gửi email thông báo** (Xử lý bất đồng bộ).

---

## 🏗️ Kiến Trúc Hệ Thống

### Sơ đồ tổng quát (System Overview)

```mermaid
graph TD
    subgraph "Client Side"
        Web[Web Frontend - React/Vue/Vite\nlocalhost:5173]
    end

    subgraph "API Gateway Layer"
        Envoy[Envoy API Gateway - Port 80\n- AuthZ Filter\n- Routing]
    end

    subgraph "Logic Layer (Microservices)"
        Identity[Identity Service :8090\nXác thực & JWT]
        User[User Service :8091\nQuản lý Profile]
        Film[Film Service :8092\nQuản lý Phim]
        Showtime[Showtime Service :8094\nLịch Chiếu]
        Email[Email Service :8093\nGửi Mail]
    end

    subgraph "Data & Messaging Layer"
        PG[(PostgreSQL :5433\n5 Databases)]
        Redis[(Redis :6379\nToken/OTP/Cache)]
        Rabbit[RabbitMQ :5672\nMessage Broker]
    end

    Web --> Envoy
    Envoy -- "1. ext_authz" --> Identity
    Identity -- "2. Valid (X-User-ID, Role)" --> Envoy
    Envoy -- "3. Forward Request" --> User
    Envoy -- "3. Forward Request" --> Film
    Envoy -- "3. Forward Request" --> Showtime
    
    Identity & User & Film & Showtime --> PG
    Identity & User & Film & Showtime --> Redis
    Identity -- "Async Message" --> Rabbit
    Rabbit --> Email
```

### Luồng xác thực qua Envoy (Gateway Pattern)

Envoy đóng vai trò là "người gác cổng" thông minh qua cơ chế **ext_authz**:

1. **Client** gửi request kèm header `Authorization: Bearer <token>` đến Envoy.
2. Envoy chặn request và gọi **ext_authz** nội bộ đến `identity-service/internal/auth/check`.
3. **Identity Service** sẽ:
   - Giải mã và xác thực JWT token (đảm bảo hạn dùng hợp lệ).
   - Kiểm tra trong **Redis** xem token có bị blacklist hay không (người dùng đã logout chưa).
4. Nếu hợp lệ, Identity Service trả về HTTP 200 kèm các header `X-User-ID` và `X-User-Role`.
5. Envoy lấy các header này từ kết quả ext_authz, gắn vào request gốc và truyền (forward) xuống service đích (User, Film, hoặc Showtime). Lớp bảo vệ này giúp các service con không cần tự quan tâm đến logic JWT phức tạp.

### Giao tiếp nội bộ giữa các Service (gRPC)

Từ bản cập nhật hiện tại, hệ thống tách rõ 2 lớp giao tiếp:

- **HTTP/REST** chỉ còn dành cho phía ngoài: Frontend -> Envoy Gateway -> Public API.
- **gRPC nội bộ** dùng cho các lệnh gọi service-to-service nhằm giảm overhead serialization, giảm độ trễ và gom contract tập trung bằng `.proto`.
- Lớp wiring gRPC hiện được chuẩn hóa theo **Spring gRPC official starter** để giảm bớt `ManagedChannel` / `ServerBuilder` config thủ công ở từng service.

Các luồng đã được chuyển sang **gRPC**:

- `identity-service` -> `user-service`: tạo profile Customer / Manager / Staff.
- Email được đẩy vào RabbitMQ để `email-service` xử lý bất đồng bộ.
- `showtime-service` -> `film-service`: lấy chi tiết phim theo `filmId`.
- `showtime-service` -> `booking-service`: chuẩn hóa contract kiểm tra showtime đã được đặt vé hay chưa.
- `showtime-service` -> `cinema-service`: lấy `cinemaId` theo `userId` (GetCinemaByUserId) để scope dữ liệu pricing policy theo rạp.

Contract gRPC được đặt tập trung trong `common-lib/src/main/proto` để mọi service dùng chung một chuẩn message/stub nội bộ. Việc bind server/client hiện đi qua cấu hình `spring.grpc.*` trong `application.yaml` cho các luồng RPC còn lại.

---

## 🛠️ Công Nghệ Sử Dụng

| Công nghệ | Version | Vai trò / Lý do lựa chọn |
|---|---|---|
| **Java** | 17 | Ngôn ngữ lập trình chính, ổn định, mạnh mẽ. |
| **Spring Boot** | 4.0.1 (core modules) | Framework chính tạo Microservices nhanh chóng. |
| **Spring Security** | Tích hợp | Xử lý JWT Authentication + Authorization. |
| **Spring Data JPA** | Tích hợp | ORM (Hibernate) truy vấn cơ sở dữ liệu chuyên sâu. |
| **Spring Data Redis** | Tích hợp | Giao tiếp với Redis qua Lettuce client. |
| **Spring AMQP** | 4.0.1 | Quản lý kết nối Message Broker (RabbitMQ). |
| **Spring Mail** | 4.0.1 | Hỗ trợ gửi template HTML qua JavaMailSender. |
| **Spring gRPC + Protocol Buffers** | Spring gRPC 1.0.2 / protoc 3.25.8 | Giao tiếp nội bộ service-to-service hiệu năng cao, dùng starter/autoconfig chính thức của Spring thay cho wiring thủ công. |
| **PostgreSQL** | 16 | RDBMS chính, ACID, hỗ trợ tốt UUID và JSON. |
| **Redis** | 7 | In-memory cache cực nhanh cho token, OTP, session, API cache. |
| **RabbitMQ** | 3.x | Hệ thống Queue xử lý các luồng bất đồng bộ (giảm latency). |
| **Envoy Proxy** | 1.31.x | API Gateway chuẩn microservices, ext_authz và routing linh hoạt. |
| **Docker Compose** | Mới nhất | Container hóa đồng bộ 100% môi trường dev/prod. |
| **Maven** | 3.9+ | Build tool mạnh mẽ quản lý dự án multi-module. |
| **jjwt** | 0.12.6 | Xử lý thế hệ Token ECDSA và RSA an toàn. |
| **MapStruct** | 1.6.3 | Tự động sinh code mapping DTO ↔ Entity siêu tốc. |
| **Lombok** | 1.18.30 | Giảm thiểu mã lặp (Getter, Setter, Builder). |
| **UUID Creator** | 5.3.7 | Tạo UUID v7 (Time-ordered) để tối ưu index B-Tree trong DB. |
| **SpringDoc** | 2.5.0 | Tự động trích xuất Swagger UI/OpenAPI document. |

---

## 📁 Cấu Trúc Dự Án

Dự án được phân tách thành các module Maven riêng biệt theo chuẩn thiết kế Domain-Driven Design (DDD).

```text
cinema-microservices/
├── common-lib/                    # 📚 Thư viện dùng chung (core standard)
│   └── src/main/java/com/cinema/
│       ├── Enum/                  # Enums: UserRole, UserStatus, FilmStatus, ShowTimeStatus
│       ├── controller/            # BaseController chuẩn hóa response REST (ok, created)
│       ├── dto/                   # APIResponse, CursorPageRequest/Response
│       ├── exception/             # GlobalExceptionHandler, ErrorCode tập trung 68 mã lỗi, BusinessException
│       ├── grpc/                  # Shared gRPC contracts, generated stubs, error utils
│       └── service/               # Service helpers dùng chung
│
├── identity-service/              # 🔐 Service xác thực và ủy quyền
│   └── src/main/java/.../
│       ├── config/                # JwtAuthenticationFilter, WebSecurityConfig, RedisConfig
│       ├── controller/            # UserController xử lý các API (/api/auth/*)
│       ├── dto/                   # Request/Response cho Account & Auth
│       ├── entity/                # Entity danh tính gốc (User, OtpData lưu memory/DB)
│       ├── mapper/                # Chuyển đổi qua MapStruct
│       ├── repository/            # Giao tiếp bảng accounts
│       ├── services/              # Logic Authentication (JWT cấp phát, verify, refresh)
│       └── utils/                 # Trình sinh mã OTP (OTPGenerator)
│
├── user-service/                  # 👤 Service quản lý hồ sơ người dùng
│   └── src/main/java/.../
│       ├── config/                # RedisConfig (Cache)
│       ├── controller/            # API hồ sơ (/api/users/*)
│       ├── entity/                # Hồ sơ chi tiết (Thông tin cá nhân, Ngân hàng)
│       └── services/              # Xử lý CRUD hồ sơ với Role tương ứng
│
├── film-service/                  # 🎬 Service quản lý kho phim
│   └── src/main/java/.../
│       ├── controller/            # API Phim (/api/films/*)
│       ├── entity/                # Film entity chứa metadata (Trailer, poster, duration...)
│       ├── repository/            # FilmRepository kèm Custom Method để Cursor Pagination
│       └── services/              # Xử lý tìm kiếm phức tạp và chuẩn hóa đầu vào phim
│
├── showtime-service/              # 📅 Service xếp lịch chiếu phim
│   └── src/main/java/.../
│       ├── controller/            # API Lịch chiếu (/api/showtimes/*)
│       ├── entity/                # ShowTime liên kết Film ID và thông tin phòng chiếu
│       └── services/              # Giải quyết bài toán xung đột lịch chiếu (Collision Detection)
│
├── email-service/                 # 📧 Service gửi thông báo
│   └── src/main/java/.../
│       └── services/              # Xử lý gửi email bất đồng bộ qua Gmail SMTP (@EnableAsync)
│
├── booking-service/               # 🎟️ Booking core service (seat lock + booking/product APIs + internal gRPC)
├── payment-service/               # 💳 Payment service (session + SePay webhook + VietQR bank catalog)
│
├── envoy/
│   ├── envoy.local.yaml           # 🔀 Envoy local (route tới host.docker.internal)
│   └── envoy.prod.yaml            # 🔀 Envoy prod (route tới service trong Docker)
│
├── postgres-init/
│   └── create-databases.sql       # 🐘 Script tự khởi động 5 DB độc lập cho microservices
│
├── compose.prod.yaml              # 🐳 Compose production/default (all services in Docker)
├── compose.local.yaml             # 🐳 Override local (Envoy route to local services)
└── pom.xml                        # 📦 Parent POM quản lý versions tập trung
```

---

## 📦 Chi Tiết Từng Service

### 1. `common-lib` (Thư viện dùng chung - Trái tim hệ thống)
Mọi service đều import thư viện này. Nó giải quyết triệt để sự lặp lại mã (DRY):
- **Dynamic API Response**: Class `APIResponse<T>` chuẩn hóa mọi payload gửi về Frontend (success, code, data, timestamp).
- **Global Error Handling**: Bắt lỗi toàn cục qua `@RestControllerAdvice`. Sử dụng Enum `ErrorCode` để quản lý tập trung mã lỗi logic (vd: 4001: USER_NOT_FOUND, 4002: FILM_TITLE_EXISTED).
- **Pagination Strategy**:
  - `film-service`: dùng **Cursor Pagination** (`CursorPageRequest`, `CursorPageResponse`) cho tập dữ liệu lớn.
  - `hall-service`, `showtime-service`, `cinema-service`: dùng **Page Pagination** (`PageRequest`, `PageResponse`) để đồng nhất payload cho FE/QA.

### 2. `identity-service` & Bảo mật (Security Model)
Đảm nhận trọng trách cổng kiểm tra chứng minh thư của hệ thống.
- **Mã hóa**: BCrypt (độ khó 10) chống brute-force bảng rainbow.
- **OTP System**: Xác minh email thực thụ. Mã sinh 6 số tự động được Redis giám sát, hết hạn sau 5 phút. Tích hợp giới hạn chỉ cho nhập sai 3 lần, nếu tái phạm sẽ lock tài khoản tạm thời.
- **Dual JWT Flow**: Khách hàng được cấp Access Token gắn trong Body (dùng trong RAM Frontend) và Refresh Token được cấp vào cấu hình chặt. 
- **Token Blacklist**: Logout là triệt để. Redis sẽ lưu vào sổ đen các JTI (Token ID) để khóa mọi Token bị hủy dù nó còn hạn sống.

### 3. `user-service`
Đóng gói chuyên biệt cho thông tin cá nhân.
- Cô lập riêng `Identity` và `Profile` để hạn chế vector tấn công lộ mật khẩu.
- Hỗ trợ lưu cấu hình Ngân hàng (`bankCode`, `accountNumber`) dành riêng cho chức năng hoàn tiền/đối soát.

### 4. `film-service`
Kho báu nội dung hệ thống.
- **Index Constraining**: Kiểm soát tính duy nhất theo partial unique index:
  - Chỉ chặn trùng `title + release_date` trên tập `is_deleted = false`.
  - Record đã xóa mềm (`is_deleted = true`) được phép trùng.
  - Script migration: `scripts/sql/2026-05-17-film-active-unique-index.sql`.
- **Soft Delete**: Mọi hành vi `DELETE` chỉ đổi cờ status DB `IsDeleted` sang True. Toàn vẹn dữ liệu cho các truy vấn báo cáo bán hàng lịch sử vẫn được bảo đảm tuyệt đối.
- **Complex Querying**: Tìm phim bằng từ khóa, lọc theo độ tuổi `AgeRating`, hoặc tình trạng hiện hành phim `FilmStatus`.
- **Future extension**: xem `FILM_REQUEST_FUTURE_NOTE.md` nếu sau này cần mở rộng luồng manager đề xuất phim chưa có trong catalog. Rule hiện tại vẫn giữ `ADMIN only` cho việc tạo phim.

### 5. `showtime-service` 
Bộ não hệ thống lập lịch chiếu phim hằng ngày.
- **Collision Rules Engine**: Không cho phép tạo đè suất chiếu. Bộ máy sẽ phân tích lịch của phòng chiếu mục tiêu, cảnh báo và từ chối nếu thời lượng bị lố giờ sang suất chiếu khác.
- Theo dõi sát vòng đời thực tiễn của suất (Pending -> Screening -> Ended).
- Quản lý **Pricing Policy** ngay trong `showtime-service` để giá vé đi cùng ngữ cảnh suất chiếu thay vì gắn cố định vào hall.
- Mỗi `showtime` hiện giữ `pricingPolicyId`, còn response showtime trả kèm object `pricingPolicy` để Frontend đọc giá trực tiếp.
- Pricing policy được scope theo `cinemaId`; service lấy `cinemaId` từ `cinema-service` qua gRPC `GetCinemaByUserId` dựa trên `X-User-ID`.
- Booking lock rule: nếu showtime có booking active (`PENDING`, `RESERVED`, `CONFIRMED`) thì chặn update status/update/delete.

### Booking Lock Rule (Global cho showtime/hall/cinema)

- Primitive dùng chung: `booking-service` gRPC `HasActiveBookingByShowtimeIds`.
- `showtime`: check trực tiếp theo `showtimeId`.
- `hall`: lấy danh sách showtime active theo hall, nếu có booking active thì chặn update/delete.
- `cinema`: lấy toàn bộ hall active theo cinema, gom showtime active của các hall, nếu có booking active thì chặn update/update-status/delete.
- Chỉ các trạng thái booking active mới chặn: `PENDING`, `RESERVED`, `CONFIRMED`.
- Booking terminal (`CANCELLED`, `EXPIRED`) không chặn.

### 6. `email-service`
Nhận job gửi mail bất đồng bộ từ **RabbitMQ** để giảm tải request đồng bộ.
- Tách luồng IO với SMTP Google chậm chạp ra khỏi Response HTTP nhằm bảo vệ tính trải nghiệm thời gian thực của người dùng khi yêu cầu OTP hoặc vé điện tử.
- Listener có retry/backoff để xử lý tạm thời khi SMTP hoặc network lỗi.

---

## 🌐 API Endpoints

### 1. Identity Service (`/api/auth`)

| Method | Endpoint | Auth | Mô tả (Chi Tiết) |
|---|---|---|---|
| `POST` | `/api/auth/register` | ❌ | Đăng ký tài khoản Customer mới, hệ thống gửi OTP. |
| `POST` | `/api/auth/verify-otp` | ❌ | Xác thực mã OTP nhận từ email (Bị khóa sau 3 lần sai). |
| `POST` | `/api/auth/login` | ❌ | Đăng nhập tài khoản, trả về cụm Access & Refresh Token. |
| `POST` | `/api/auth/logout` | ❌ | Đăng xuất, đưa token hiện hành vào danh sách Blacklist Redis. |
| `POST` | `/api/auth/refresh_token` | ❌ | Trao đổi Refresh Token lấy Access Token mới. |
| `POST` | `/api/auth/forgot-password` | ❌ | Báo quên mật khẩu, đẩy một email mang OTP reset. |
| `POST` | `/api/auth/resend-otp` | ❌ | Yêu cầu gửi lại OTP cho các hành động trên. |
| `POST` | `/api/auth/change-password` | ✅ | Người dùng đổi mật khẩu mới (Cần Auth hợp lệ). |
| `POST` | `/api/auth/manager` | ✅ ADMIN | Cấp tài khoản cấp độ Manager. |
| `POST` | `/api/auth/staff` | ✅ ADMIN/MANAGER | Cấp tài khoản nhân sự Staff. |
| `GET` | `/internal/auth/check` | ✅ | Internal API. Trạm kiểm soát của Envoy. |

### 2. User Service (`/api/users`)

| Method | Endpoint | Auth | Mô tả |
|---|---|---|---|
| `POST` | `/api/users/customers` | Internal | Khởi tạo Profile KH sau khi Verify Identity. |
| `GET` | `/api/users/me` | ✅ Authenticated | Lấy thông tin profile của user hiện tại theo `X-User-ID`. |
| `PUT` | `/api/users/customers` | ✅ CUST | Cập nhật hồ sơ cá nhân. |
| `GET` | `/api/users/exists/{userId}` | Internal | Kiểm tra nhanh chéo Service xem User tồn tại không. |
| `GET/PUT/POST` | `/api/users/staffs|managers`| ✅ ADMIN | Các nghiệp vụ CRUD quản lý nhân sự chuyên quản. |

### 3. Film Service (`/api/films`)

| Method | Endpoint | Auth | Mô tả |
|---|---|---|---|
| `POST` | `/api/films/search` | ❌ Public | Tìm kiếm danh mục phim kết hợp Cursor Pagination + Filters. |
| `GET` | `/api/films/{id}` | ❌ Public | Lấy nguyên mẫu thông tin phim chi tiết (có đánh Cache Redis). |
| `POST` | `/api/films` | ✅ ADMIN | Đăng tải thông tin Phim mới. |
| `PUT` | `/api/films/{id}` | ✅ ADMIN | Chỉnh sửa cập nhật nội dung Phim. |
| `DELETE` | `/api/films/{id}` | ✅ ADMIN | Xóa mềm bộ phim khỏi hệ thống kinh doanh. |

### 4. Showtime Service (`/api/showtimes`)

| Method | Endpoint | Auth | Mô tả |
|---|---|---|---|
| `POST` | `/api/showtimes/search` | ❌ Public | Lấy danh sách suất chiếu theo `PageRequest`, trả full thông tin `film`, `hall`, `pricingPolicy` và thêm `totalSeatCapacity`, `occupiedSeats`, `availableSeats` để FE render trạng thái chỗ trống. |
| `POST` | `/api/showtimes` | ✅ MGMT | Tạo lịch chiếu hàng loạt theo khung giờ và gắn `pricingPolicyId` cho toàn bộ batch. |
| `PUT` | `/api/showtimes/{id}` | ✅ MGMT | Cập nhật chi tiết một showtime gồm thời gian, trạng thái và `pricingPolicyId`. |
| `PATCH` | `/api/showtimes/{id}` | ✅ MGMT | Tinh chỉnh nhanh trạng thái showtime. |
| `DELETE` | `/api/showtimes/{id}` | ✅ ADMIN | Bỏ lịch chiếu hệ thống (Cũng dùng Xóa Mềm). |
| `GET` | `/api/showtimes/{id}` | ❌ Public | Lấy chi tiết suất chiếu và trả full thông tin `film`, `hall`, `pricingPolicy` và trạng thái chỗ còn trống. |

### 4.1. Showtime Pricing Policy (`/api/showtimes/pricing-policies`)

| Method | Endpoint | Auth | Mô tả |
|---|---|---|---|
| `POST` | `/api/showtimes/pricing-policies` | ✅ MGMT | Tạo mới một policy giá cho ghế `STANDARD`, `VIP`, `COUPLE`. |
| `PUT` | `/api/showtimes/pricing-policies/{id}` | ✅ MGMT | Cập nhật bộ giá của policy, chỉ áp dụng khi policy chưa được showtime nào sử dụng. |
| `DELETE` | `/api/showtimes/pricing-policies/{id}` | ✅ MGMT | Xóa mềm policy giá, chỉ áp dụng khi policy chưa được showtime nào sử dụng. |
| `GET` | `/api/showtimes/pricing-policies/{id}` | ✅ MGMT | Lấy chi tiết một policy giá trong cinema hiện tại của user. |
| `GET` | `/api/showtimes/pricing-policies` | ✅ MGMT | Lấy danh sách policy giá của cinema hiện tại. |

### 5. Payment Service (`/api/payments`)

| Method | Endpoint | Auth | Mô tả |
|---|---|---|---|
| `GET` | `/api/payments/vietqr/banks` | ❌ Public | Lấy danh mục ngân hàng từ local catalog đã sync từ VietQR. |
| `POST` | `/api/payments/sessions` | ✅ Authenticated | Tạo phiên thanh toán cho booking hiện tại (có ownership check theo `X-User-ID`). |
| `GET` | `/api/payments/sessions/{bookingId}` | ✅ Authenticated | Lấy phiên thanh toán mới nhất theo booking (scope theo user). |
| `POST` | `/api/payments/sessions/{bookingId}/refund` | ✅ Authenticated | Tạo yêu cầu hoàn tiền nội bộ (trạng thái `REFUND_PENDING`). |
| `GET` | `/api/payments/reconciliation` | ✅ Authenticated | Tổng hợp đối soát theo khoảng thời gian (`from`, `to`). |
| `POST` | `/api/payments/promotions/preview` | ✅ Authenticated | Ước tính giảm giá từ promo code trước checkout. |
| `POST` | `/api/payments/webhooks/sepay` | ❌ Public (secret header) | Nhận webhook từ SePay, idempotent theo event key. |

### 6. Hall Service (`/api/halls`)

| Method | Endpoint | Auth | Mô tả |
|---|---|---|---|
| `POST` | `/api/halls` | ✅ MGMT | Tạo hall mới với `layoutJson` (JSON sơ đồ ghế để FE render). |
| `GET` | `/api/halls/{id}` | ❌ Public | Lấy chi tiết hall và trả `layoutJson` đầy đủ. |
| `POST` | `/api/halls/search` | ❌ Public | Tìm hall bằng `PageRequest/PageResponse`. |
| `PATCH` | `/api/halls/{id}/layout` | ✅ MGMT | Cập nhật toàn bộ `layoutJson` của hall. |

### 7. Cinema Service (`/api/cinemas`)

| Method | Endpoint | Auth | Mô tả |
|---|---|---|---|
| `POST` | `/api/cinemas` | ✅ ADMIN | Tạo cinema mới. |
| `GET` | `/api/cinemas/{id}` | ❌ Public | Lấy chi tiết một cinema theo id. |
| `POST` | `/api/cinemas/search` | ❌ Public | Tìm kiếm cinema bằng `PageRequest/PageResponse`. |
| `PUT` | `/api/cinemas/{id}` | ✅ ADMIN | Cập nhật thông tin cinema (địa chỉ, tọa độ, giờ hoạt động, manager). |
| `PATCH` | `/api/cinemas/{id}` | ✅ ADMIN | Cập nhật nhanh trạng thái cinema. |
| `DELETE` | `/api/cinemas/{id}` | ✅ ADMIN | Xóa mềm cinema và ngắt active staff mapping liên quan. |
| `POST` | `/api/cinemas/{id}/staffs` | ✅ ADMIN | Gán staff vào cinema. |
| `PUT` | `/api/cinemas/{id}/staffs` | ✅ ADMIN | Upsert/re-assign staff vào cinema theo `staffId`. |
| `DELETE` | `/api/cinemas/{id}/staffs/{staffId}` | ✅ ADMIN | Gỡ staff khỏi cinema (đánh dấu inactive). |
| `GET` | `/api/cinemas/{id}/staffs` | ❌ Public | Lấy danh sách staff mapping của cinema. |
| `GET` | `/api/cinemas/me` | ✅ MANAGER | Lấy cinema hiện tại do manager đang quản lý. |

Ghi chú gateway:
- Envoy đã route theo prefix `/api/cinemas/`, nên các method mới (bao gồm `PUT /api/cinemas/{id}/staffs`) không cần thêm route mới.

---

## 🐘 Cơ Sở Dữ Liệu & Caching

### Chiến Lược "Database per Service"
Tránh điểm chết thắt cổ chai của dạng cơ sở dữ liệu liền khối (Monolithic Database) truyền thống. Khóa liên kết ngoài (Foreign Key Constraints) bị loại bỏ có tính toán, các ID liên kết bằng chuẩn phân tán UUID.
Với việc Envoy điều phối và routing tải chia, khi có nhu cầu thì Postgresql có thể tự được dời cụm cluster riêng ra.

Chín cơ sở dữ liệu gồm: `identity_db`, `user_db`, `film_db`, `showtime_db`, `hall_db`, `cinema_db`, `booking_db`, `payment_db`, `seat_db` được nhúng tự động thông qua khối lệnh của `/postgres-init/create-databases.sql`.

### Chiến Lược Redis
Áp dụng kho Redis v7 cung cấp băng thông nghìn Request/sec:
- **Tầng Xác Thực:** Giữ token vào RAM. Kể cả Database sập Identity Auth vẫn hoạt động liên tục vì Redis ôm hoàn toàn dữ liệu.
- **Tầng Tạm Thời (Transient):** OTP mã hóa 6 số gán định trạng thái phá hủy (TTL - 300 giây).
- **Tầng Gateway:** Envoy cache có thể được bật thêm khi cần để giảm Database roundtrip (hiện chưa bật trong config).

---

## 🚀 Hướng Dẫn Cài Đặt

### Yêu cầu hệ thống

- **Java 17** (JDK).
- **Maven 3.9+**.
- **Docker & Docker Compose**.
- Giới hạn RAM tối thiểu kiến nghị thiết bị **8GB** (Lý tưởng 16GB).
- **Git**.

### Bước 1: Clone dự án

```bash
git clone <repository-url>
cd cinema-microservices
```

### Bước 2: Chuẩn bị file `.env` siêu quan trọng

Tạo tệp `.env` cho mỗi một cấu trúc Service trong các thư mục `src/main/resources/`. Cụ thể:
- `identity-service/src/main/resources/.env`
- `user-service/src/main/resources/.env`
- `film-service/src/main/resources/.env`
- `showtime-service/src/main/resources/.env`
- `email-service/src/main/resources/.env`

*(Tham khảo mục **Cấu hình môi trường** ở dưới để biết các biến cần ghi).*
> ⚠️ **Lưu ý:** File `.env` chứa chìa khóa hệ thống. KHÔNG BAO GIỜ được mang commit lên cấu trúc mạng Git Public!

### Bước 3: Đóng gói (Build) thông qua Maven Multi-Module

Tại thư mục góc `cinema-microservices`:
```bash
# Lệnh tải sạch cache dependency và nén file thực thi chạy Jar nhưng Bỏ qua Test để nhanh hơn
mvn clean install -DskipTests
```

---

## ▶️ Hướng Dẫn Chạy

### Cách 1: Vận chuyển siêu tốc bằng Docker Compose (Đề xuất)

Dựng toàn bộ kiến trúc chỉ trong một dòng lệnh Terminal, tất cả hạ tầng (DB, Message Queue) và API Service đều đồng khởi chạy và tạo mạng nội bộ với nhau.
```bash
docker compose -f compose.prod.yaml up -d

# Check tình trạng cỗ máy
docker compose -f compose.prod.yaml ps
# Quản lý xem log ngẫu nhiên một Service
docker compose -f compose.prod.yaml logs -f identity-service
```

### Chạy Gateway Envoy theo chế độ Dev/Prod

- **Prod (default):** Envoy route tới service trong Docker network.
```bash
docker compose -f compose.prod.yaml up -d
```
- **Dev (service chạy local VS Code):** Envoy route về `host.docker.internal`.
```bash
docker compose -f compose.prod.yaml -f compose.local.yaml up -d
```

### Tương quan Port Mạng Trở Về:

| Dịch vụ / Hệ Tầng | Liên kết thực thi trên máy cá nhân |
|---|---|
| Envoy (Trung Tâm Gateway) | http://localhost:80 |
| Identity Service Web API | http://localhost:8090 (service local default) |
| User Profile Web API | http://localhost:8091 (service local default) |
| PostgreSQL Relational | `localhost:5433` (User: postgres / 123456) |
| Redis In-Memory KV | `localhost:6379` |
| RabbitMQ Management UI | http://localhost:15672 (admin / admin) |

*Muốn Tắt Toàn Bộ Dữ Liệu?*
```bash
docker compose -f compose.prod.yaml down -v
```

---

## ⚙️ Cấu Hình Môi Trường

Soạn thảo mã chuẩn cho toàn bộ file `.env` (Chỉnh URL, Password phù hợp):

**Cấu Trúc Các Base Chung (Copy đặt cho mọi file .env):**
```env
# === Database Connection ===
DB_URL=jdbc:postgresql://pg:5432/<database_name_tuong_ung>
DB_USERNAME=postgres
DB_PASSWORD=123456
JPA_DDL_AUTO=update
JPA_SHOW_SQL=true

# === Redis Connect ===
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_TIMEOUT=5000

# === RabbitMQ Core ===
SPRING_RABBITMQ_HOST=rabbitmq
SPRING_RABBITMQ_PORT=5672
SPRING_RABBITMQ_USERNAME=admin
SPRING_RABBITMQ_PASSWORD=admin
```

**Biến Đặc Hữu Của Từng Dịch Vụ Cần Thêm (Cắt Dán Kèm Vào):**

**[identity-service]**
```env
SERVER_PORT=8090
USER_GRPC_HOST=user-service
USER_GRPC_PORT=9191
```

**[user-service]**
```env
SERVER_PORT=8091
GRPC_SERVER_PORT=9191
```

**[film-service]**
```env
SERVER_PORT=8092
GRPC_SERVER_PORT=9192
```

**[showtime-service]**
```env
SERVER_PORT=8094
FILM_GRPC_HOST=film-service
FILM_GRPC_PORT=9192
BOOKING_GRPC_HOST=booking-service
BOOKING_GRPC_PORT=9195
CINEMA_GRPC_HOST=cinema-service
CINEMA_GRPC_PORT=9196
```

**Schema note for `showtime-service`:**
- `show_time` có cột `pricing_policy_id` để liên kết logic tới bảng `pricing_policy`.
- `pricing_policy` lưu bộ giá cơ bản cho `STANDARD`, `VIP`, `COUPLE`.
- `pricing_policy` có thêm cột `cinema_id` để phân tách bộ giá theo từng cinema.
- Khi client gọi API showtime, response sẽ trả kèm `pricingPolicyId` và object `pricingPolicy`.
- Rule nghiệp vụ: `pricing_policy` đã được gán cho bất kỳ `showtime` nào thì không được update hoặc delete nữa.
- Rule nghiệp vụ bổ sung: policy chỉ được truy cập/sử dụng khi thuộc đúng cinema của manager hiện tại.


**[hall-services]**
```env
SERVER_PORT=8097
GRPC_SERVER_PORT=9197
DB_URL=jdbc:postgresql://pg:5432/hall_db
CINEMA_GRPC_HOST=cinema-service
CINEMA_GRPC_PORT=9196
```

**Schema note for `hall-services`:**
- `hall` lưu `layout_json` dạng JSON để FE render sơ đồ ghế.
- `layout_json` hỗ trợ loại ô ghế và ô `AISLE` (đường đi).
- gRPC nội bộ `GetHallById` dùng cho service khác (ví dụ `showtime-service`).

**[email-service]**
```env
SERVER_PORT=8093
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_FROM_NAME=CinemaSystem
MAIL_USERNAME=nhap_mail@gmail.com
MAIL_PASSWORD=mat_khau_ung_dung_app_pass_cua_ban
```

> Khi chạy local không qua Docker network, có thể đổi các giá trị `*_GRPC_HOST` về `localhost`.
> `compose.prod.yaml` đã đồng bộ các host/port gRPC chính cho các service nội bộ; khi thêm service mới chỉ cần nối theo cùng convention này.
> Bên trong từng service, các biến môi trường này được map vào cấu hình `spring.grpc.client.channels.*.address` hoặc `spring.grpc.server.port`.

---

## 🗺️ Trạng Thái Lộ Trình Hiện Tại (25/05/2026)

- Core đã hoàn thành: identity, user, film, showtime, cinema, hall, booking, payment, gateway, gRPC nội bộ, DB bootstrap.
- Còn mở rộng: tách `product-service` riêng, tích hợp refund với cổng thanh toán thực tế, loyalty và khuyến mãi nâng cao.
- Các mục ở phần lịch sử phía dưới là nhật ký triển khai, không phải backlog chưa làm.

---

## 🗺️ Lịch Sử Phát Triển

### 🗓️ 01/04/2026 — Rà soát Tái Cấu Trúc Toàn Diện

**Đánh giá trạng thái thành quả Core:**

✅ **Các Mảnh Ghép Xong Nhiệm Vụ:**
1. Khung Sườn Multi-Module Microservices với chuẩn Common Library phân bố.
2. Hoàn tất Envoy Gateway áp dụng ext_authz và routing chuẩn, thay thế Nginx trong kiến trúc.
3. Bộ Token hoàn thiện (Role Check, OTP Timeout 5min Limit 3 lần, Refresh Token Flow).
4. Khảm Redis Error Handler Logic phân tán tải cực mạnh.
5. Triển khai Cursor Paging vào khối dữ liệu Film Data để tăng cường sức truy xuất vô hạn không giảm tốc.
6. Centralized Auto DB Script Configuration cho Container Init.
7. Áp mã UUID v7 thay cho v4 vào Primary Key.

⚠️ **Các Hạng Mục Đã Hoàn Thành / Còn Mở Rộng:**

| Hạng Mục | Trạng Thái | Diễn Giải Nhiệm Vụ |
|---|---|---|
| **Hall Service (Rạp & Ghế)** | ✅ Hoàn thành | Đã có sơ đồ rạp, phòng chiếu, layout ghế và route gateway đồng bộ. |
| **Booking Core Service** | ✅ Hoàn thành | Xương sống đặt vé, giữ ghế Redis, booking/product APIs, gRPC nội bộ và rule tối đa 5 vé. |
| **Payment Integration** | ✅ Hoàn thành core | Đã có session thanh toán, webhook SePay, confirm booking qua gRPC, refund/reconciliation/promotion preview. |

### 🗓️ 02/04/2026 — Nâng cấp Phân trang (Offset Pagination)
**Nội dung cập nhật:**
- Bổ sung bộ DTO phân trang truyền thống `PageRequest` và `PageResponse` vào `common-lib`.
- Refactor API lấy danh sách nhân sự (Staff/Manager) tại `user-service`. Dịch chuyển từ trả về List toàn bộ sang phân trang tối ưu.
- Thay đổi Endpoints:
  - `POST /api/users/staffs/search` (Phân trang Staff).
  - `POST /api/users/managers/search` (Phân trang Manager).

### 🗓️ 03/04/2026 — Tích hợp liên Service: Showtime & Booking
**Nội dung cập nhật:**
- Xây dựng giao thức kiểm tra trạng thái đặt vé giữa `showtime-service` và `booking-service`.
- Gắn REST Endpoint `GET /api/bookings/check-showtime/{showtimeId}` để tra cứu xem một suất chiếu đã phát sinh giao dịch đặt vé hay chưa.
- Chặn thay đổi trạng thái (Update Status) hoặc Xóa Phim (Delete) ở Showtime nếu như nhận được cờ báo đã có người đặt vé.
- Bổ sung mã lỗi `BOOKING_SERVICE_ERROR("9104")` vào thư viện `common-lib`.
- Bổ sung API `GET /api/showtimes/{id}` để hệ thống Frontend có thể nạp chi tiết một suất chiếu độc lập chuyên sâu thay vì chỉ tra cứu qua danh sách Search.
- Bổ sung Batch API `POST /api/films/batch` tại `film-service` hỗ trợ Frontend thực hiện Client-Side API Composition thay vì gom gọi (N+1 query) gây nghẽn rớt dây chuyền.
  - Sử dụng DTO chuẩn xác `BatchFilmRequest` (có `@NotEmpty`) và `BatchFilmResponse` để đóng gói dữ liệu an toàn chặn lỗi từ sớm.

### 🗓️ 03/04/2026 — Nâng cấp Inter-Service Communication sang gRPC
**Nội dung cập nhật:**
- Chuyển các lệnh gọi nội bộ đang dùng REST sang **gRPC** ở các luồng:
  - `identity-service` -> `user-service`
  - Email được đẩy vào RabbitMQ để `email-service` xử lý bất đồng bộ.
  - `showtime-service` -> `film-service`
- `showtime-service` -> `booking-service` (theo contract gRPC thống nhất)
- Bổ sung thư mục `common-lib/src/main/proto` chứa các contract:
  - `user_internal.proto`
  - `email_internal.proto`
  - `film_internal.proto`
  - `booking_internal.proto`
- Sinh shared gRPC stubs vào `common-lib` để các service tái sử dụng cùng một contract nội bộ.
- Thêm gRPC server bootstrap cho `user-service`, `film-service`.
- Thêm gRPC client config cho `identity-service`, `showtime-service`.
- Dọn bỏ `RestTemplate` nội bộ ở các luồng trên để tách hẳn REST public và RPC nội bộ.
- Dọn luồng gửi mail trong `identity-service`: bỏ `new Thread(...)` thủ công, giữ `@Async` + gRPC để giảm thread thừa và dễ kiểm soát hơn.
- Đồng bộ `compose.prod.yaml` cho cặp service đã có Dockerfile (`identity-service`, `user-service`) để container gọi nhau qua host nội bộ thay vì rơi về `localhost`.
- Sửa lại một số điểm cấu hình đi kèm:
  - `user-service` trả về mặc định đúng cổng HTTP `8091`.
  - `email-service` chuẩn hóa cổng HTTP `8093` để phục vụ nội bộ.
  - `identity-service` sửa cách đọc header phân quyền ở các luồng tạo `manager/staff` và đổi mật khẩu.

### 🗓️ 04/04/2026 — Chuẩn hóa gRPC theo Spring Official Starter
**Nội dung cập nhật:**
- Refactor lớp wiring gRPC từ cấu hình Java thủ công sang **Spring gRPC official starter** (`org.springframework.grpc:spring-grpc-spring-boot-starter`) để phù hợp hơn với hệ Spring Boot 4.
- Loại bỏ các class config tự tạo `ManagedChannel`, `BlockingStub`, `GrpcServerRunner` ở từng service.
- Chuyển client-side wiring sang `GrpcChannelFactory`, nhờ đó wrapper gRPC chỉ cần lấy channel theo tên (`user`, `email`, `film`, `booking`) thay vì tự build connection thủ công.
- Giữ nguyên các contract `.proto` và generated stubs trong `common-lib`, nên luồng giao tiếp nội bộ không bị đổi semantics.
- Chuẩn hóa prefix cấu hình từ `grpc.*` cũ sang `spring.grpc.*` trong `application.yaml`:
  - `spring.grpc.server.port`
  - `spring.grpc.client.channels.<name>.address`
- Biên dịch kiểm tra lại toàn bộ multi-module bằng Maven sau khi refactor và bảo đảm build thành công.
- Refactor chung header nội bộ sang hằng số dùng chung (`X-User-ID`, `X-User-Role`) để tránh sai lệch và giảm duplication.
- Cải thiện độ an toàn khi parse cursor: dùng URL decoder tương thích và bỏ lỗi khi cursor không hợp lệ.
- Tránh mutate list sort đầu vào trong search showtime để giảm side-effect khó debug.
- Dọn duplicate `authentication.setDetails` trong filter để giảm work thừa.
- Tối ưu query phim và showtime: tránh thao tác `toLowerCase` lặp lại, dùng `IN` cho enum status, thêm guard khi cursor thiếu field và ưu tiên so khớp UUID trực tiếp khi keyword là UUID.

### 🗓️ 05/04/2026 — Chuẩn hóa API Gateway với Envoy (Production-ready)
**Nội dung cập nhật:**
- Thay Nginx gateway bằng **Envoy Proxy** để phù hợp hơn với kiến trúc microservices/gRPC.
- Cấu hình `ext_authz` gọi `identity-service/internal/auth/check` để xác thực tập trung và tự động forward `X-User-ID`, `X-User-Role`.
- Thiết lập routing cho các service backend qua Envoy.
- Tách 2 cấu hình Envoy cho **local/prod** qua `envoy.local.yaml` và `envoy.prod.yaml` + `compose.local.yaml`.

### 09/04/2026 - Showtime pricing policy
**Nội dung cập nhật:**
- Loại bỏ DTO `ShowTimeWithFilmResponse`; thống nhất tất cả API showtime trả về `ShowTimeResponse` (đã gồm `film`, `hall`, `pricingPolicy`).
- Loại bỏ endpoint cũ `/api/showtimes/search-with-film` và `/api/showtimes/{id}/with-film`; thay bằng `/api/showtimes/search` và `/api/showtimes/{id}`.
- Thêm entity `PricingPolicy` vào `showtime-service` để quản lý giá `STANDARD`, `VIP`, `COUPLE`.
- Thêm cột `pricingPolicyId` vào `ShowTime` và nối vào luồng tạo showtime hàng loạt.
- Mở rộng response `showtime` để trả kèm `pricingPolicyId` và object `pricingPolicy`.
- Thêm cột `cinemaId` vào `pricing_policy` và validate policy theo cinema.
- Tích hợp gRPC `showtime-service` -> `cinema-service` (`GetCinemaByUserId`) để resolve `cinemaId` theo `X-User-ID`.
- Bổ sung `PUT /api/showtimes/{id}` để cập nhật chi tiết showtime, bao gồm thay đổi policy giá.
- Bổ sung CRUD API cho `pricing policy`:
  - `POST /api/showtimes/pricing-policies`
  - `PUT /api/showtimes/pricing-policies/{id}`
  - `DELETE /api/showtimes/pricing-policies/{id}`
  - `GET /api/showtimes/pricing-policies/{id}`
  - `GET /api/showtimes/pricing-policies`
- Khóa nghiệp vụ cho pricing policy: chỉ được sửa/xóa khi policy đó chưa được showtime nào sử dụng.

### 10/04/2026 - Hall layoutJson and AISLE update
**Nội dung cập nhật:**
- Refactor `hall-services` theo model `Hall` dùng `layoutJson` (JSON) thay vì bảng `Seat` rời.
- API hall cho FE gồm: `POST /api/halls`, `GET /api/halls/{id}`, `POST /api/halls/search`, `PATCH /api/halls/{id}/layout`.
- Bổ sung seat type `AISLE` để biểu diễn ô đường đi trong sơ đồ ghế.
- Bổ sung gRPC server `HallInternalService/GetHallById` và cấu hình `GRPC_SERVER_PORT=9197`.
- Cập nhật `compose.prod.yaml`, `compose.local.yaml`, `envoy.local.yaml`, `envoy.prod.yaml`, `postgres-init/create-databases.sql` để deploy đồng bộ.
- Điều chỉnh `showtime-service` dùng `HALL_GRPC_PORT` mặc định `9197`.
- Build compile đã pass với Maven local (`mvn`) cho `hall-service` + `common-lib`.
- Chuẩn hóa lại nội dung tổng kết README theo mốc cập nhật mới nhất ngày `10/04/2026`.

### 08/05/2026 - Booking service product MVP (gộp tài liệu về file chung)
**Nội dung cập nhật:**
- Nối `booking-service` vào parent multi-module (`pom.xml`) để build chung theo chuẩn repo.
- Bổ sung DB bootstrap cho booking:
  - `booking_user`
  - `booking_db`
  - grant/revoke tương ứng trong `postgres-init/create-databases.sql`.
- Nâng `booking-service` từ skeleton sang REST + JPA + Validation + gRPC client.
- Bổ sung entity `Product` trong `booking-service` (MVP chưa tách service riêng):
  - `cinemaId`, `name`, `type`, `price`, `description`, `imageUrl`, `status`, `isDeleted`, `timeCreated`, `timeUpdated`.
- Chuẩn hóa mapper theo style các service hiện có (MapStruct):
  - `ProductMapper.toEntity(...)`
  - `ProductMapper.updateEntityFromRequest(...)`
  - `ProductMapper.toResponse(...)`.
- Bổ sung gRPC `CinemaGrpcClient` để resolve `cinemaId` từ `X-User-ID` qua `GetCinemaByUserId` cho luồng operator.
- Bổ sung API product trong `booking-service`:
  - `POST /api/bookings/products`
  - `PUT /api/bookings/products/{id}`
  - `DELETE /api/bookings/products/{id}`
  - `GET /api/bookings/products/{id}`
  - `GET /api/bookings/products/me` (manager/staff theo cinema đăng nhập)
  - `GET /api/bookings/products/cinemas/{cinemaId}` (customer theo cinema đã chọn)
- Rule nghiệp vụ áp dụng:
  - Soft delete cho product.
  - Unique tên product trong cùng `cinemaId` (trên tập record chưa xóa).
  - `create/update/delete` được scope theo cinema của operator.
- Build check đã pass bằng Maven Wrapper:
  - `booking-service\\mvnw.cmd -DskipTests compile`
  - `booking-service\\mvnw.cmd -DskipTests test-compile`
- Mở rộng booking core cho phase đặt vé:
  - Bổ sung entity:
    - `Booking`
    - `BookingSeatItem`
    - `BookingProductItem`
    - `CustomerInfo` (embedded)
  - Bổ sung enums:
    - `BookingStatus` (`PENDING`, `RESERVED`, `CONFIRMED`, `CANCELLED`, `EXPIRED`)
    - `PaymentStatus` (`UNPAID`, `PAID`, `FAILED`, `REFUNDED`)
  - Bổ sung API booking:
    - `POST /api/bookings`
    - `GET /api/bookings/{id}`
    - `POST /api/bookings/me/search`
    - `POST /api/bookings/cinemas/me/search`
    - `GET /api/bookings/me/active`
    - `PATCH /api/bookings/{id}/status`
    - `POST /api/bookings/{id}/cancel`
  - Booking snapshot:
    - Lưu snapshot ngay lúc tạo booking để history/search tự đủ dữ liệu:
      - `customerInfo.fullName/email/phone`
      - `filmTitle`
      - `showtimeStartDateTime`
      - `showtimeEndDateTime`
    - `customerInfo` luôn bắt buộc; `userId` chỉ là liên kết bổ sung khi booking gắn với tài khoản đăng nhập.
    - History/search tìm theo `customerInfo.fullName` và `filmTitle`, không cần gọi lại `UserService` hoặc `ShowtimeService` khi đọc lịch sử.
  - Chuẩn request frontend:
    - Chỉ dùng **1 payload** `CreateBookingRequest` khi khách đã chọn xong toàn bộ ghế + sản phẩm.
    - `customerInfo`, `seatItems`, `productItems` được thiết kế dạng nested object/list trong cùng request.
  - Redis seat lock:
    - Khóa ghế theo key `booking:seat-lock:{showtimeId}:{seatCode}`.
    - TTL mặc định `5` phút (`booking.seat-lock-minutes`).
    - Khi lock thất bại giữa chừng, rollback lock đã set trước đó.
    - Khi `cancel` hoặc status chuyển terminal (`CANCELLED`, `EXPIRED`, `CONFIRMED`) thì release lock.
  - Auto-expire booking:
    - Scheduler quét booking quá `reservedUntil` và tự chuyển sang `EXPIRED`.
    - Nhịp quét cấu hình bằng env `BOOKING_EXPIRATION_SCHEDULER_DELAY_MS` (mặc định 30 phút).
    - Khi expire sẽ release lại Redis seat lock tương ứng.
  - Rule nghiệp vụ booking đã áp:
    - Tối đa `5` ghế mỗi booking.
    - Không cho seat trùng trong cùng request.
    - Snapshot tên/giá sản phẩm vào `BookingProductItem`.
    - Unique `(booking_id, seat_code)` và `(booking_id, product_id)`.
  - gRPC booking internal:
    - Triển khai `BookingInternalService/CheckShowtimeBooked` trong `booking-service`.
    - Mục tiêu: cho `showtime-service` kiểm tra suất chiếu đã có booking hay chưa trước khi update/delete.
    - Cấu hình server port: `spring.grpc.server.port` (`GRPC_SERVER_PORT`, mặc định `9195`).
  - Runtime wiring để service khác gọi được:
    - Bổ sung `booking-service` vào `compose.prod.yaml` (HTTP `8095`, gRPC `9195`).
    - Thêm route và cluster `/api/bookings` trong `envoy.prod.yaml` và `envoy.local.yaml`.

**Decision log (vì sao chọn như hiện tại):**
- Bối cảnh:
  - MVP cần lên nhanh chức năng menu bắp/nước để phục vụ đặt vé.
  - `booking-service` thời điểm bắt đầu vẫn là skeleton nên ưu tiên làm thẳng trong service này trước.
- Quyết định 1: chưa tách `product-service`, giữ `Product` trong `booking-service`.
  - Lý do: giảm độ phức tạp triển khai ban đầu, tránh tăng số lượng service khi nghiệp vụ chưa ổn định.
  - Hệ quả chấp nhận: sau này khi scale lớn có thể phải tách service và làm migration.
- Quyết định 2: dùng MapStruct mapper (`toEntity`, `updateEntityFromRequest`, `toResponse`) giống `film-service`/`showtime-service`.
  - Lý do: đồng nhất style code toàn repo, dễ review, giảm mapping thủ công.
- Quyết định 3: tách 2 luồng API đọc danh sách product:
  - Operator (`MANAGER`/`STAFF`): `GET /api/bookings/products/me` lấy cinema theo account đăng nhập.
  - Customer: `GET /api/bookings/products/cinemas/{cinemaId}` lấy theo cinema đã chọn trên UI.
  - Lý do: customer không có ngữ cảnh "cinema quản lý", nên phải dựa vào cinema được chọn.
- Quyết định 4: `GET /api/bookings/products/{id}` đọc theo `id` thuần.
  - Lý do: endpoint chi tiết by-id cần trả đúng record theo định danh; không ép scope manager cho use case đọc chi tiết cơ bản.
- Quyết định 5: create/update/delete vẫn scope theo account operator qua `GetCinemaByUserId`.
  - Lý do: tránh sửa dữ liệu xuyên cinema.
- Quyết định 6: booking create dùng một request tổng hợp duy nhất cho frontend.
  - Lý do: đúng flow checkout thực tế (khách chọn ghế + bắp nước xong mới submit 1 lần).
- Quyết định 7: dùng Redis lock ghế TTL 5 phút trong lúc chờ thanh toán.
  - Lý do: chặn tranh chấp ghế theo thời gian thực, đồng thời tự giải phóng lock khi hết hạn.
  - Rule chốt hiện tại:
  - Soft delete (`isDeleted`) cho product.
  - Unique tên product trong cùng `cinemaId` trên tập chưa xóa.
  - Trạng thái mặc định khi tạo nếu request không truyền: `ACTIVE`.
  - Các điểm chưa làm ở phase này:
  - Chưa tách `product-service` riêng khỏi `booking-service`.
  - Payment đã có session + webhook callback + refund nội bộ + reconciliation/promotion preview, nhưng chưa tích hợp gọi API refund thực tế với cổng thanh toán.
---
> Hệ thống được thiết kế theo kiến trúc mở và đã được rà soát tổng thể toàn bộ luồng xử lý đến **25/05/2026**. Mục tiêu là sẵn sàng đáp ứng quy mô hệ thống đặt vé trực tuyến yêu cầu High Availability.

---

## 🤖 Nhật ký hành động AI

### 2026-04-10 18:59 (UTC+07:00) - Tạo skill ghi log vào README
- Yêu cầu: rà nhanh các skill hiện có và tạo một skill để ghi lại từng yêu cầu mới vào `README.md`.
- Hành động: tạo `.agents/skills/readme-action-logger/SKILL.md` và thiết lập quy tắc append log cho mỗi tác vụ hoàn thành.
- File: `.agents/skills/readme-action-logger/SKILL.md`, `README.md`
- Kết quả: kho mã đã sẵn sàng dùng cơ chế ghi log vào README cho các yêu cầu tiếp theo.

### 2026-04-10 19:00 (UTC+07:00) - Chuyển log AI sang tiếng Anh
- Yêu cầu: viết các mục nhật ký hành động bằng tiếng Anh.
- Hành động: cập nhật quy tắc của skill logger để buộc ghi log bằng tiếng Anh và dịch mục log hiện có sang tiếng Anh.
- File: `.agents/skills/readme-action-logger/SKILL.md`, `README.md`
- Kết quả: các log AI đã được chuẩn hóa sang tiếng Anh.

### 2026-04-10 19:20 (UTC+07:00) - Chẩn đoán và xác minh build toàn dự án
- Yêu cầu: rà lại toàn bộ dự án vì đang lỗi và xác định nguyên nhân.
- Hành động: chạy kiểm tra compile trên tất cả module, rebuild và cài lại `common-lib`, cài parent POM vào local Maven repo, rồi kiểm tra lại compile của từng service.
- File: `README.md`
- Kết quả: tất cả module đều compile thành công với `-DskipTests` trong lần xác minh local.

### 2026-04-10 20:15 (UTC+07:00) - Mở rộng response hall với payload cinema
- Yêu cầu: thêm `cinemaResponse` vào `HallResponse` và giữ log hành động trong README được cập nhật.
- Hành động: thêm DTO `CinemaResponse` trong `hall-service`, mở rộng `HallResponse` với `cinemaResponse`, map dữ liệu từ `HallServiceImpl`, và cập nhật quy tắc ignore của mapper cho field mới.
- File: `hall-service/src/main/java/com/cinema/hall_services/dto/response/CinemaResponse.java`, `hall-service/src/main/java/com/cinema/hall_services/dto/response/HallResponse.java`, `hall-service/src/main/java/com/cinema/hall_services/services/impl/HallServiceImpl.java`, `hall-service/src/main/java/com/cinema/hall_services/mapper/HallMapper.java`, `README.md`
- Kết quả: response API hall hiện có thêm object `cinemaResponse` chứa id cinema (và có thể mở rộng tên cinema sau này).

### 2026-04-10 20:20 (UTC+07:00) - Sửa route Envoy cho `POST /api/halls`
- Yêu cầu: điều tra lỗi `404 NR` khi gọi `POST /api/halls` từ Postman.
- Hành động: cập nhật route Envoy để khớp cả `/api/halls` và `/api/halls/` trong `envoy.local.yaml` và `envoy.prod.yaml`.
- File: `envoy/envoy.local.yaml`, `envoy/envoy.prod.yaml`, `README.md`
- Kết quả: gateway đã xử lý đúng API hall dù có hoặc không có dấu gạch chéo cuối đường dẫn.

### 2026-04-10 20:25 (UTC+07:00) - Sửa lệch package Jackson cho `layoutJson`
- Yêu cầu: xử lý `HttpMessageConversionException` cho `HallCreateRequest.layoutJson`.
- Hành động: chuyển import JSON của hall-service từ `com.fasterxml.jackson.*` sang `tools.jackson.*` (Spring 7/Jackson 3), bao phủ DTO request, DTO response và xử lý JSON trong service.
- File: `hall-service/src/main/java/com/cinema/hall_services/dto/request/HallCreateRequest.java`, `hall-service/src/main/java/com/cinema/hall_services/dto/request/UpdateHallLayoutRequest.java`, `hall-service/src/main/java/com/cinema/hall_services/dto/response/HallResponse.java`, `hall-service/src/main/java/com/cinema/hall_services/services/impl/HallServiceImpl.java`, `README.md`
- Kết quả: `layoutJson` được Spring deserialize đúng bằng các type của Jackson 3.

### 2026-04-10 20:30 (UTC+07:00) - Loại bỏ xử lý JSON deprecated trong hall service
- Yêu cầu: sửa các method deprecated và ổn định lại xử lý JSON của hall service.
- Hành động: tái cấu trúc `HallServiceImpl` với `JsonMapper.builder().build()`, thay các kiểm tra integer bằng luồng `isIntegralNumber()` không deprecated, khôi phục cách suy ra `cinemaId` từ `X-User-ID` (bỏ giá trị hardcode), và sửa logic `parseSeatType` bị lỗi cú pháp.
- File: `hall-service/src/main/java/com/cinema/hall_services/services/impl/HallServiceImpl.java`, `README.md`
- Kết quả: hall service không còn dùng API JSON deprecated và quay lại cơ chế xác định cinema theo manager scope.

### 2026-04-10 20:33 (UTC+07:00) - Loại bỏ các method textual deprecated của JsonNode
- Yêu cầu: thay `isTextual()` và `asText()` deprecated trong validation layout hall.
- Hành động: cập nhật `HallServiceImpl` sang pattern `TextNode.textValue()` của Jackson 3 trong `readRequiredText` và `parseSeatType`, loại bỏ API textual deprecated.
- File: `hall-service/src/main/java/com/cinema/hall_services/services/impl/HallServiceImpl.java`, `README.md`
- Kết quả: xử lý text trong layout hall không còn phụ thuộc các method textual deprecated của JsonNode.

### 2026-04-22 00:10 (UTC+07:00) - Thêm endpoint PUT cho staff cinema và chuyển mapper sang MapStruct
- Yêu cầu: thêm endpoint `PUT` còn thiếu cho gán staff vào cinema và đồng bộ cách triển khai mapper với các service khác.
- Hành động: thêm `PUT /api/cinemas/{id}/staffs` qua controller/service/serviceImpl, chuyển mapper của `cinema-service` từ class thủ công sang interface MapStruct, và cập nhật `cinema-service/pom.xml` với cấu hình processor của MapStruct.
- File: `cinema-service/src/main/java/com/cinema/cinema_service/controller/CinemaController.java`, `cinema-service/src/main/java/com/cinema/cinema_service/services/CinemaService.java`, `cinema-service/src/main/java/com/cinema/cinema_service/services/impl/CinemaServiceImpl.java`, `cinema-service/src/main/java/com/cinema/cinema_service/mapper/CinemaMapper.java`, `cinema-service/pom.xml`, `README.md`, `TECHNICAL_AGENT_GUIDE.md`
- Kết quả: API staff của cinema hỗ trợ flow `PUT` idempotent và style mapper đồng nhất với các service dùng MapStruct.

### 2026-04-22 00:40 (UTC+07:00) - Sửa kiểu generic RedisConfig và serializer deprecated
- Yêu cầu: điều tra lỗi compile liên quan đến `GenericObjectPoolConfig<?>` và `GenericJackson2JsonRedisSerializer`.
- Hành động: cập nhật kiểu generic của Redis pool thành `GenericObjectPoolConfig<StatefulConnection<?, ?>>` và thay `GenericJackson2JsonRedisSerializer` deprecated bằng `GenericJacksonJsonRedisSerializer` trong các cấu hình Redis.
- File: `identity-service/src/main/java/com/cinema/identity_service/config/RedisConfig.java`, `user-service/src/main/java/com/cinema/user_service/config/RedisConfig.java`, `film-service/src/main/java/com/cinema/film_service/config/RedisConfig.java`, `showtime-service/src/main/java/com/cinema/showtime_service/config/RedisConfig.java`, `README.md`, `TECHNICAL_AGENT_GUIDE.md`
- Kết quả: bỏ được lỗi generic không tương thích trong cấu hình pooling của Lettuce và loại bỏ serializer Redis deprecated ở các service liên quan.

### 2026-05-13 07:05 (UTC+07:00) - Chuyển phân trang cinema search sang PageRequest/PageResponse
- Yêu cầu: refactor phân trang của `cinema-service` từ request kiểu cursor sang request kiểu page.
- Kỹ năng đã dùng: `backend-dev-guidelines`, `api-documentation`.
- Hành động:
  - Chuyển kiểu request của `POST /api/cinemas/search` từ `CursorPageRequest<CinemaField>` sang `PageRequest<CinemaField>`.
  - Chuyển kiểu response từ `CursorPageResponse<CinemaResponse>` sang `PageResponse<CinemaResponse>`.
  - Tái cấu trúc luồng search để dùng `page/size` và tự tính `totalElements`, `totalPages`, `hasNext`, `hasPrevious`.
  - Thêm các method repository `searchWithPageAndSortAndFilter(...)` và `countWithFilter(...)` nhưng vẫn giữ nguyên keyword/filter/sort.
  - Xác minh compile bằng Maven build chạy trong Docker.
- File:
  - `cinema-service/src/main/java/com/cinema/cinema_service/controller/CinemaController.java`
  - `cinema-service/src/main/java/com/cinema/cinema_service/services/CinemaService.java`
  - `cinema-service/src/main/java/com/cinema/cinema_service/services/impl/CinemaServiceImpl.java`
  - `cinema-service/src/main/java/com/cinema/cinema_service/repository/CinemaRepositoryImpl.java`
- Kết quả: API search cinema hiện hỗ trợ payload phân trang theo page:
  - `{"page":1,"size":12,"keyword":"","filterBy":[],"sortBy":[]}`

### 2026-05-13 07:20 (UTC+07:00) - Thêm `managerName` vào `CinemaResponse`
- Yêu cầu: thêm field `managerName` vào response cinema cho frontend sử dụng.
- Kỹ năng đã dùng: `backend-dev-guidelines`, `api-documentation`.
- Hành động:
  - Thêm `managerName` vào `CinemaResponse`.
  - Cập nhật mapping trong `CinemaMapper` để ignore rõ ràng `managerName`, giúp MapStruct compile an toàn với `ReportingPolicy.ERROR`.
  - Xác minh compile bằng Docker build.
- File:
  - `cinema-service/src/main/java/com/cinema/cinema_service/dto/response/CinemaResponse.java`
  - `cinema-service/src/main/java/com/cinema/cinema_service/mapper/CinemaMapper.java`
- Kết quả: response API hiện có thêm field `managerName` (tạm thời có thể null cho đến khi nối nguồn tên manager).

### 2026-05-13 07:35 (UTC+07:00) - Nối `managerName` cho cinema search/getById qua gRPC user-service
- Yêu cầu: trả về `managerName` thật trong `GET /api/cinemas/{id}` và `POST /api/cinemas/search`.
- Kỹ năng đã dùng: `backend-dev-guidelines`, `api-documentation`.
- Hành động:
  - Thêm RPC nội bộ `GetUserBasicById` trong `user_internal.proto`.
  - Implement handler RPC ở lớp gRPC của `user-service` và expose `UserService.getUserById(...)`.
  - Thêm `UserGrpcClient` trong `cinema-service` và enrich `managerName` cho `getById` + `search`.
  - Thêm cấu hình channel gRPC trong `cinema-service` và biến runtime trong `compose.prod.yaml`.
- Kết quả: `managerName` hiện được lấy từ `user-service` cho response search/getById, và tự fallback về `null` nếu `user-service` không khả dụng.


---

## Sổ tay tác nghiệp AI (bổ sung mới)

### A. Quy tắc ra quyết định nhanh

1. Thay đổi chỉ 1 service, không đổi contract: sửa local service.
2. Thay đổi payload liên service: bắt đầu từ `common-lib/src/main/proto`.
3. Thay đổi auth/permission: kiểm tra cả Envoy + identity + business service.
4. Thay đổi lỗi nghiệp vụ: map vào `ErrorCode`, tránh hardcode string.
5. Thay đổi response cho frontend: cập nhật DTO + mapper + controller docs.

### B. Checklist bắt buộc trước khi kết thúc task

1. Có vi phạm scope theo `cinemaId` không?
2. Có kiểm tra role (`X-User-Role`) đúng chưa?
3. Có fallback khi gRPC fail chưa?
4. Có lỗi message tiếng Việt không dấu không?
5. Có đổi config env/port thì compose/envoy đã đồng bộ chưa?
6. Có ảnh hưởng contract cũ không (backward compatibility)?

### C. Chiến lược debug production-like

- Bước 1: xác định lỗi ở lớp nào (Gateway / Service / DB / MQ / Redis).
- Bước 2: grep `error code` trong `common-lib` trước để map business context.
- Bước 3: trace request path qua headers nội bộ (`X-User-ID`, `X-User-Role`).
- Bước 4: nếu liên service, test gRPC consumer trước rồi mới nhìn provider.
- Bước 5: chỉ refactor khi đã có reproduction rõ.

### D. Template note khi mở PR

- Scope:
- Impacted services:
- Proto change:
- Breaking change risk:
- Envoy/Compose change:
- Fallback strategy:
- Test evidence:

---

Cập nhật kỹ thuật gần nhất: 13/05/2026.

### 2026-05-20 - Tối giản API hall
- Trạng thái: Đã chấp nhận
- Kỹ năng đã dùng: `architecture-decision-records`, `api-design-principles`

#### Bối cảnh
- FE dùng 1 màn hình duy nhất để tạo/sửa hall.
- FE gửi full payload mỗi lần submit (`name`, `status`, `layoutDefinition`).
- Kiến trúc mới đã tách seat canonical sang `seat-service` (gRPC nội bộ).

#### Các phương án đã cân nhắc
1. Giữ 3 DTO + 3 endpoint riêng (`HallCreateRequest`, `UpdateHallRequest`, `UpdateHallStatusRequest`)
- Ưu điểm: endpoint chuyên biệt, dễ phân quyền chi tiết.
- Nhược điểm: FE phải ghép nhiều flow, dễ gọi nhầm endpoint, contract phức tạp.

2. Gộp 1 DTO chung cho create/update, bỏ patch status riêng
- Ưu điểm: FE đơn giản, contract gọn, đúng với UX 1 form.
- Nhược điểm: cần bảo vệ nghiệp vụ khi update layout trong lúc đang có booking.

#### Quyết định
- Chọn phương án 2.
- Tạo DTO chung `HallUpsertRequest` cho cả `POST /api/halls` và `PUT /api/halls/{id}`.
- Bỏ endpoint `PATCH /api/halls/{id}` cập nhật status riêng.
- Bỏ các DTO cũ: `HallCreateRequest`, `UpdateHallRequest`, `UpdateHallStatusRequest`.
- `createHall` và `updateHall` đều xử lý `layoutDefinition` thông qua `seat-service` (gRPC).

#### Hệ quả
- Tích cực:
  - FE chỉ còn 1 contract để tạo/sửa hall.
  - Backend dễ bảo trì hơn, ít DTO bị lặp.
  - Vẫn giữ được rule an toàn: chặn sửa layout nếu hall đang có booking active.
- Đánh đổi:
  - Mất endpoint status riêng; mỗi lần đổi status đi qua full form submit.

#### Ghi chú an toàn
- `PUT /api/halls/{id}` vẫn kiểm tra `HALL_LAYOUT_IN_USE` trước khi replace layout.
- Nếu seat-layout chưa tồn tại, backend fallback tạo layout trong flow update.

### 2026-05-20 - Tinh chỉnh hợp đồng form hall
- Trạng thái: Đã chấp nhận (thay thế phiên bản `2026-05-20 - Tối giản API hall`)
- Kỹ năng đã dùng: `architecture-decision-records`, `api-design-principles`

#### Bối cảnh
- FE vẫn dùng 1 giao diện tạo/sửa hall, gửi full payload.
- Team muốn tên DTO rõ nghĩa nghiệp vụ (`Create`/`Update`) thay vì `Upsert`.
- Hall có nhiều ảnh, cần gửi danh sách ảnh trong cùng request hall thay vì API thêm từng ảnh.

#### Quyết định
- Tách lại DTO:
  - `HallCreateRequest`
  - `UpdateHallRequest`
- Cả 2 DTO đều gồm:
  - `name`, `status`, `layoutDefinition`, `imagePaths: List<String>`
- Bỏ endpoint thêm ảnh riêng `POST /api/halls/{id}/images`.
- Đồng bộ ảnh theo full payload trong create/update:
  - Ảnh có trong request: tạo mới/khôi phục nếu đã soft-delete.
  - Ảnh không còn trong request: soft-delete.

#### Hệ quả
- Tích cực:
  - Tên request rõ nghĩa và quen thuộc cho team.
  - FE vẫn chỉ cần 1 form, 1 lần submit là cập nhật cả hall + layout + images.
  - Không cần quản lý API thêm ảnh riêng.
- Đánh đổi:
  - Update là full-replace image list, FE phải gửi đầy đủ danh sách ảnh hiện tại.

### 2026-05-29 Tra cứu chi tiết user qua REST
- Yêu cầu: mở `GET /api/users/{id}` để đội support/vận hành tra cứu chi tiết user theo `userId` với kiểm soát quyền theo role.
- Quyết định: `ADMIN` đọc được mọi user; `MANAGER` đọc được tất cả `CUSTOMER` và chỉ đọc được `STAFF` khi staff đó thuộc rạp mà manager đang quản lý; `STAFF` chỉ đọc được toàn bộ `CUSTOMER`; `CUSTOMER` vẫn ở `/me`.
- Ghi chú: luồng gRPC nội bộ `getUserById(UUID)` vẫn giữ nguyên; route REST mới tái sử dụng cùng dữ liệu service với lớp kiểm tra quyền trong `user-service`, và phạm vi rạp giữa manager/staff được xác thực qua gRPC `cinema-service`.

### 2026-05-29 API tìm kiếm customer
- Yêu cầu: thêm API tìm kiếm customer có phân trang, tương tự `POST /api/users/staffs/search`.
- Quyết định: mở `POST /api/users/customers/search` và cho `ADMIN` / `MANAGER` / `STAFF` dùng để tra cứu toàn bộ customer phục vụ vận hành.
- Ghi chú: endpoint này dùng cùng pattern `PageRequest` / `PageResponse` như search staff/manager và trả hồ sơ customer trực tiếp từ `user-service`.

### 2026-05-29 Báo cáo doanh thu theo rạp
- Yêu cầu: xây dựng báo cáo doanh thu cho `ADMIN` và `MANAGER`, phân trang theo rạp, và trả cùng lúc `page` + `total` summary trong cùng một response.
- Quyết định: mở `POST /api/payments/revenues/cinemas/search` cho admin và `POST /api/payments/revenues/cinemas/me/search` cho manager.
  - Ghi chú:
    - Báo cáo tính theo lúc thực thu, không theo thời điểm tạo giao dịch.
    - `paidAt` là sự kiện tiền vào, `refundedAt` là sự kiện tiền ra.
    - Request report bổ sung `cinemaIds` và `filmIds` để lọc trước khi aggregate theo rạp.
    - Frontend gửi `dateRange` riêng, tách khỏi `pageRequest`.
    - Nếu không truyền `dateRange` thì report lấy toàn bộ dữ liệu.
    - `page` là summary của các rạp đang hiển thị ở trang hiện tại.
  - `total` là summary của toàn bộ phạm vi đã lọc.
  - Phạm vi rạp lấy từ gRPC `cinema-service`, và các rạp không có doanh thu trong khoảng lọc vẫn phải được hiển thị với giá trị 0.
  - `payment_transaction` snapshot thêm `filmId` lấy từ booking context để report không phải join ngược sang booking khi chạy.
  - Tài liệu giải thích đầy đủ và danh sách file liên quan: [REVENUE_REPORT_RULES.md](./REVENUE_REPORT_RULES.md)

### 2026-05-29 Tìm kiếm film giữ cursor và ưu tiên phim mới nhất
- Yêu cầu: giữ `CursorPageRequest` cho `film-service`, đồng thời cho phép lọc theo khoảng ngày phát hành.
- Quyết định: search film tiếp tục dùng cursor paging, mặc định sắp xếp `releaseDate DESC` rồi tie-break bằng `id ASC`, và nếu frontend gửi `dateRange` thì lọc theo `releaseDate` trong khoảng đó.
- Ghi chú:
  - Không đổi sang `PageRequest` cho film vì catalog phim lớn.
  - `dateRange` là payload riêng, tách khỏi dữ liệu cursor.
  - Mục tiêu hiển thị mặc định là phim mới phát hành trước.

### 2026-05-29 Báo cáo bán hàng ở booking-service
- Yêu cầu: thêm một báo cáo riêng để nhìn giá trị đơn hàng phát sinh, tách bạch với báo cáo doanh thu thực thu của `payment-service`.
- Quyết định:
  - Mở `POST /api/bookings/revenues/cinemas/search` cho `ADMIN`.
  - Mở `POST /api/bookings/revenues/cinemas/me/search` cho `MANAGER`.
  - Dữ liệu báo cáo lấy từ `Booking`, không lấy từ `PaymentTransaction`.
  - Mốc thời gian của báo cáo là `Booking.timeCreated`.
  - Request report bổ sung `cinemaIds` và `filmIds` để lọc trước khi aggregate theo rạp.
  - Booking chưa thanh toán vẫn được tính vào giá trị đơn hàng; chỉ loại các booking đã `CANCELLED` hoặc `EXPIRED`.
  - Ghi chú:
    - Response dùng cùng kiểu `items` + `page` + `total` như báo cáo bên payment để FE render thống nhất.
    - `pageRequest` giữ vai trò phân trang/lọc/sắp xếp theo rạp.
    - Nếu frontend không truyền `dateRange` thì report lấy toàn bộ dữ liệu.
    - `Booking` snapshot thêm `filmId` để report theo phim dùng được trực tiếp từ booking data.
  - `dateRange` gửi riêng trong body request, không nhét vào `PageRequest`.

### Mẫu request gửi FE
- Nếu không lọc theo ngày thì bỏ hẳn `dateRange` khỏi body.
- Nếu có lọc theo ngày thì `dateRange` phải có đủ `from` và `to`.
- Toàn bộ payload request mẫu cho frontend đã được gom vào [API_PAYLOAD_SAMPLES.md](API_PAYLOAD_SAMPLES.md).


