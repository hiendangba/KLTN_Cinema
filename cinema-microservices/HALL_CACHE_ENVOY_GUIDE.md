# Triển khai Cache Hall và Giải thích Envoy (22-04-2026)

## 1. Mục tiêu

- Bật cache cho dữ liệu phòng chiếu (`hall-service`) để giảm truy vấn DB ở các luồng đọc lặp lại (đặc biệt `getHallById` dùng cho gRPC và API).
- Tránh lỗi kiểu `LinkedHashMap cannot be cast ...` khi đọc cache object bằng cách thống nhất serializer Redis giống các service đã chuẩn hóa.
- Ghi lại cách Envoy hoạt động trong hệ thống để dễ vận hành và debug.

## 2. Các thay đổi đã làm

### 2.1 Bật Redis cho hall-service

1. Thêm dependency Redis vào `hall-service`:
- `spring-boot-starter-data-redis`
- `commons-pool2`

File:
- `hall-service/pom.xml`

2. Thêm cấu hình `spring.data.redis` cho `hall-service`:
- host, port, username, password
- timeout, connect-timeout
- lettuce pool

File:
- `hall-service/src/main/resources/application.yaml`

3. Thêm biến Redis trong file `.env` local của hall-service:

File:
- `hall-service/src/main/resources/.env`

### 2.2 Thêm RedisConfig cho hall-service

Tạo mới:
- `hall-service/src/main/java/com/cinema/hall_service/config/RedisConfig.java`

Điểm chính:
- `@EnableCaching`
- `RedisTemplate<String, Object>`
- `CacheManager` với TTL 30 phút
- Cache name: `halls`
- Prefix cache theo app name: `hall-services::halls::...`
- Serializer:
  - `GenericJacksonJsonRedisSerializer.builder().enableUnsafeDefaultTyping().build()`

Lý do chọn serializer này:
- Giữ type metadata khi lưu object vào Redis.
- Hạn chế lỗi deserialize thành `LinkedHashMap` rồi cast lỗi về DTO.

### 2.3 Bật cache cho luồng đọc Hall theo ID

File:
- `hall-service/src/main/java/com/cinema/hall_service/services/impl/HallServiceImpl.java`

Áp dụng:
- `getHallById(UUID id)`:
  - `@Cacheable(value = RedisConfig.CACHE_HALLS, key = "#id")`

- Các API cập nhật/xóa hall:
  - `updateHall(...)`
  - `updateHallStatus(...)`
  - `updateHallLayout(...)`
  - `deleteHall(...)`
  - đều gắn `@CacheEvict(value = RedisConfig.CACHE_HALLS, key = "#hallId")`

Ý nghĩa:
- Đọc lần đầu -> query DB -> lưu cache.
- Đọc lần sau cùng `hallId` -> trả từ cache.
- Khi có chỉnh sửa/xóa -> xóa cache key tương ứng để không trả dữ liệu cũ.

### 2.4 Cập nhật deploy production

File:
- `compose.prod.yaml`

Cập nhật service `hall-services`:
- thêm env Redis:
  - `REDIS_HOST`
  - `REDIS_PORT`
  - `REDIS_USERNAME`
  - `REDIS_PASSWORD`
- thêm `depends_on: redis`

## 3. Cách Envoy đang chạy trong project

### 3.1 Vai trò tổng quát

Envoy là API Gateway đứng trước tất cả microservice:
- Nhận request từ internet.
- Route vào service nội bộ theo prefix path.
- Chặn xác thực bằng `ext_authz` qua `identity-service`.
- Thêm header người dùng cho upstream (`x-user-id`, `x-user-role`) khi xác thực thành công.

File chính:
- `envoy/envoy.prod.yaml`

### 3.2 Luồng request qua Envoy

1. Client gọi vào domain gateway.
2. Envoy match route theo path:
   - `/api/auth/**` -> `identity-service` (được tắt `ext_authz` cho route này).
   - `/api/halls/**` -> `hall-services`.
   - `/api/cinemas/**` -> `cinema-service`.
   - các route khác tương tự.
3. Trước khi forward (trừ route auth), Envoy gọi `identity-service/internal/auth/check` qua `ext_authz`.
4. `identity-service` validate token (cookie hoặc Authorization).
5. Nếu hợp lệ:
   - trả 200 cho Envoy.
   - Envoy cho request đi tiếp đến service đích.
6. Nếu không hợp lệ:
   - Envoy trả `401` (log thường thấy `UAEX`).

### 3.3 Vì sao hay gặp 401 UAEX

Các nguyên nhân phổ biến:
- Cookie không được browser gửi (SameSite/Secure/credentials).
- Cookie hết hạn hoặc token không còn trong Redis.
- Header Authorization không có hoặc không hợp lệ.

### 3.4 Header/cookie quan trọng trong ext_authz

Envoy đang cho phép gửi sang auth-check:
- `authorization`
- `cookie`

Và cho phép trả về upstream:
- `x-user-id`
- `x-user-role`

Điều này giúp `hall-service`, `cinema-service` đọc role/user từ header để phân quyền nội bộ.

## 4. Cách kiểm tra nhanh sau khi deploy

1. Kiểm tra hall-service đã nhận cấu hình Redis:

```bash
docker exec -it cinema-deploy-hall-services-1 printenv | grep REDIS
```

2. Gọi `GET /api/halls/{id}` hai lần, lần 2 phải nhanh hơn.

3. Xem key cache trong Redis:

```bash
docker exec -it redis-container redis-cli
KEYS "hall-services::halls::*"
```

4. Gọi `PATCH/PUT/DELETE` hall rồi kiểm tra key tương ứng đã bị evict.

## 5. Quyết định kiến trúc cho case hiện tại

- Nên cache `hall` theo ID: Có lợi rõ vì đọc nhiều, đổi ít.
- Chưa cần cache `cinema-service` ngay: chưa phải điểm nóng bằng hall.
- Nếu tương lai cần tối ưu tiếp:
  - thêm cache cho `searchHalls` với key theo request đã normalize
  - hoặc cache theo read-model riêng cho booking/showtime.

