# Redis trong project này: Giải thích chi tiết cho người mới + Nhật ký thay đổi

Ngày cập nhật: 2026-04-21  
Phạm vi: Toàn bộ thay đổi liên quan Redis trong project `cinema-microservices`  
Mục tiêu: Giúp bạn hiểu Redis từ gốc, hiểu Redis đang chạy thế nào trong project, và hiểu lý do từng thay đổi đã làm.

---

## 0) Redis là gì? (Giải thích thật dễ hiểu)

### 0.1 Redis là database kiểu gì?

Redis là một **in-memory data store** (lưu dữ liệu trên RAM), mô hình chủ yếu là **key-value**.
- Key: giống tên biến
- Value: dữ liệu tương ứng với key đó

Ví dụ:
- key: `identity:otp:user123`
- value: `849231`

Vì lưu trên RAM nên Redis rất nhanh, thường dùng để:
1. Cache dữ liệu đọc nhiều
2. Lưu session/token tạm thời
3. Rate limit
4. Queue nhẹ/counter/distributed lock (tùy use-case)

### 0.2 Tại sao Redis nhanh?

Vì:
1. Đọc/ghi RAM thay vì disk
2. Cấu trúc dữ liệu tối ưu
3. Protocol rất nhẹ

Đổi lại:
- RAM đắt hơn disk
- Nếu không cấu hình persistence/replication tốt thì có rủi ro mất dữ liệu khi sự cố

### 0.3 TTL là gì?

TTL = Time To Live (thời gian sống của key).
- Sau TTL, key tự hết hạn.

Ví dụ OTP TTL 5 phút:
- 10:00 tạo OTP
- 10:05 key bị Redis xóa tự động

Đây là lý do Redis rất hợp OTP/token tạm thời.

### 0.4 Cache hit / cache miss là gì?

Giả sử endpoint lấy thông tin phim:
1. App đọc key cache `film-service::films::<id>`
2. Nếu có dữ liệu: **cache hit** (trả ngay, rất nhanh)
3. Nếu không có: **cache miss**
   - App query DB
   - Ghi lại Redis
   - Trả response

Mục tiêu của caching là tăng hit ratio để giảm tải DB.

### 0.5 Vì sao phải có connection pool?

App không nên tạo kết nối Redis mới cho mọi request (rất tốn).
Nên dùng pool:
- Có sẵn N kết nối
- Request mượn connection, dùng xong trả lại

Pool tốt giúp:
- Ổn định latency
- Tránh mở quá nhiều connection
- Kiểm soát hành vi khi tải cao

### 0.6 Serialization là gì?

Java object không thể lưu thẳng vào Redis theo nghĩa text thuần.
Phải serialize object thành dạng có thể lưu (JSON/binary...).
Trong project này dùng JSON serializer để dễ tương thích và dễ debug.

---

## 1) Redis trong project này đang làm những việc gì?

### 1.1 `identity-service`

Redis dùng cho:
1. OTP
2. Access token / refresh token state
3. Danh sách token theo user (hỗ trợ logout/cleanup)

Tức là Redis đóng vai trò **state tạm thời cho auth flow**.

### 1.2 `film-service`

Redis dùng cho cache dữ liệu phim qua `@Cacheable`.
Mục tiêu: giảm read load vào PostgreSQL, tăng tốc API đọc.

### 1.3 `user-service`, `showtime-service`

Có sẵn hạ tầng cấu hình Redis đồng bộ (để cache/state nếu mở rộng logic).

### 1.4 Redis container trong `compose.prod.yaml`

Đây là runtime Redis khi deploy compose production.

---

## 2) Trước khi tối ưu có vấn đề gì?

1. Pooling chưa được cấu hình theo kiểu tường minh, dễ lệch với kỳ vọng production.
2. Timeout cấu hình chưa tối ưu cho đa môi trường.
3. Có rủi ro chờ tài nguyên quá lâu khi Redis nghẽn.
4. Key namespace identity chưa đồng bộ hết.
5. Cache phim có nguy cơ stale sau update/delete.
6. Redis runtime prod chưa hardening đủ rõ (healthcheck/memory policy/persistence).
7. Local thiếu default Redis env nên dễ fail khi dev.

---

## 3) Những thay đổi đã làm và lý do cực kỳ cụ thể

## 3.1 Chuyển sang Lettuce Pooling tường minh

File:
- `identity-service/src/main/java/com/cinema/identity_service/config/RedisConfig.java`
- `user-service/src/main/java/com/cinema/user_service/config/RedisConfig.java`
- `film-service/src/main/java/com/cinema/film_service/config/RedisConfig.java`
- `showtime-service/src/main/java/com/cinema/showtime_service/config/RedisConfig.java`

Đã làm:
- Dùng `LettucePoolingClientConfiguration` + `GenericObjectPoolConfig`.
- Đọc các tham số pool từ `application.yaml`.

Vì sao:
- Hệ thống lớn cần kiểm soát rõ số lượng kết nối hoạt động.
- Tránh mở connection tràn lan khi có burst traffic.

Hiệu ứng thực tế:
- Ổn định hơn ở giờ cao điểm
- Dễ tuning theo traffic thật

Trade-off:
- Cần monitor pool metrics để chỉnh `max-active/max-idle`.


## 3.2 Bổ sung dependency `commons-pool2`

File:
- `identity-service/pom.xml`
- `user-service/pom.xml`
- `film-service/pom.xml`
- `showtime-service/pom.xml`

Đã làm:
- Thêm `org.apache.commons:commons-pool2`.

Vì sao:
- Lettuce pooling cần implementation của object pool.

Nếu không làm:
- Có thể cấu hình pool nhưng runtime không đúng kỳ vọng.


## 3.3 Chuẩn hóa timeout bằng `Duration`

File:
- 4 file `RedisConfig.java` ở trên.

Đã làm:
- Dùng `Duration` cho:
  - connect-timeout
  - command-timeout
  - pool max-wait
  - shutdown-timeout

Vì sao:
- Tránh nhầm đơn vị ms/s.
- Hỗ trợ config linh hoạt `500ms`, `2s`, `1m`.

Lợi ích:
- Ít lỗi cấu hình khi deploy qua nhiều môi trường.


## 3.4 Chuẩn hóa serializer JSON cho Redis

File:
- 4 file `RedisConfig.java`.

Đã làm:
- Dùng `GenericJackson2JsonRedisSerializer` + `JavaTimeModule`.

Vì sao:
- Tránh lỗi serialize/deserialize khi object chứa time type.
- Dễ debug dữ liệu cache hơn.

Trade-off:
- JSON có thể tốn RAM hơn binary một chút.


## 3.5 Namespace cache key theo service

File:
- 4 file `RedisConfig.java`.

Đã làm:
- Prefix key cache theo `appName::cacheName::`.

Vì sao:
- Nhiều service dùng chung Redis thì phải chống đụng key.

Ví dụ:
- `film-service::films::123`
- `identity-service::otp::abc`

Lợi ích:
- Tránh overwrite key cross-service.


## 3.6 Sửa stale cache của phim

File:
- `film-service/src/main/java/com/cinema/film_service/services/impl/FilmServiceImpl.java`

Đã làm:
- Thêm `@CacheEvict` cho `updateFilm` và `deleteFilm`.

Vì sao:
- Đọc có cache thì ghi phải invalidate cache.

Nếu không làm:
- User update phim xong nhưng API đọc vẫn trả dữ liệu cũ.


## 3.7 Chuẩn hóa keyspace OTP/token ở identity

File:
- `identity-service/src/main/java/com/cinema/identity_service/config/JwtAuthenticationFilter.java`
- `identity-service/src/main/java/com/cinema/identity_service/services/impl/UserServiceImpl.java`

Đã làm:
- Đồng bộ prefix key:
  - `identity:otp:`
  - `identity:otp:subject:`
  - `identity:token:access:`
  - `identity:token:refresh:`
  - `identity:user_tokens:`
- Lưu userId dưới dạng string thống nhất.
- Xóa key theo batch khi logout.

Vì sao:
- Dễ vận hành, dễ query key trong Redis CLI.
- Batch delete giảm số lần round-trip.


## 3.8 Cấu hình YAML an toàn hơn cho local + rõ hơn cho prod

File:
- `identity-service/src/main/resources/application.yaml`
- `user-service/src/main/resources/application.yaml`
- `film-service/src/main/resources/application.yaml`
- `showtime-service/src/main/resources/application.yaml`

Đã làm:
- Bổ sung `connect-timeout`, `client-name`.
- Fallback local:
  - `REDIS_HOST:localhost`
  - `REDIS_PORT:6379`
  - user/pass rỗng
- `max-wait` mặc định hữu hạn (2000ms) thay vì vô hạn.

Vì sao:
- Dev local chạy dễ hơn khi chưa set đầy đủ env.
- Khi Redis quá tải, fail-fast tốt hơn chờ vô hạn.


## 3.9 Hardening Redis runtime trong `compose.prod.yaml`

File:
- `compose.prod.yaml`

Đã làm:
- Bật AOF
- Thiết lập save snapshot rules
- Set `maxmemory` + `maxmemory-policy allkeys-lru`
- Set tcp keepalive
- Thêm healthcheck `redis-cli ping`

Vì sao:
- Production cần hành vi rõ ràng khi đầy RAM và khi restart.
- Healthcheck giúp hệ thống phụ thuộc biết Redis đã sẵn sàng.

Trade-off:
- Persistence có overhead ghi.
- Policy `allkeys-lru` cần sizing RAM đủ tốt.

---

## 4) Redis hoạt động thế nào trong luồng thật của project?

## 4.1 Luồng đọc phim có cache

1. Client gọi API lấy phim theo ID.
2. `@Cacheable("films")` kiểm tra Redis trước.
3. Nếu có key -> trả luôn (hit).
4. Nếu không có -> query DB -> trả dữ liệu + ghi cache.

## 4.2 Luồng update/delete phim

1. API update/delete chạy vào DB.
2. `@CacheEvict` xóa key cache phim tương ứng.
3. Lần đọc sau sẽ lấy dữ liệu mới từ DB và cache lại.

## 4.3 Luồng auth/token của identity

1. Khi login/refresh, service sinh token id.
2. Lưu key access/refresh vào Redis kèm TTL.
3. Lưu mapping `identity:user_tokens:<userId>` để quản lý token của user.
4. Khi logout, lấy danh sách token của user rồi xóa batch key liên quan.

---

## 5) Các thông số bạn cần hiểu để tự tuning sau này

### 5.1 Pool

- `max-active`: số connection tối đa có thể mượn cùng lúc.
- `max-idle`: số connection nhàn rỗi tối đa giữ lại.
- `min-idle`: số connection nhàn rỗi tối thiểu.
- `max-wait`: request chờ tối đa bao lâu để mượn connection.

Gợi ý tư duy:
- Nếu thường xuyên timeout vì "cannot get resource from pool" -> xem tăng `max-active` hoặc tối ưu truy cập Redis.
- Nếu idle quá nhiều -> giảm `max-idle` để tiết kiệm tài nguyên.

### 5.2 Timeout

- `connect-timeout`: timeout khi tạo kết nối ban đầu.
- `command-timeout`: timeout khi gửi lệnh Redis.

Tư duy:
- Quá thấp -> fail nhiều dù hệ thống chỉ hơi chậm.
- Quá cao -> request treo lâu, dồn thread.

### 5.3 TTL

- OTP TTL ngắn (5 phút) là hợp lý.
- Access/refresh token TTL phải theo security policy.
- Cache phim TTL nên cân bằng giữa tốc độ và độ mới dữ liệu.

---

## 6) Danh sách file đã thay đổi

- `identity-service/src/main/java/com/cinema/identity_service/config/RedisConfig.java`
- `user-service/src/main/java/com/cinema/user_service/config/RedisConfig.java`
- `film-service/src/main/java/com/cinema/film_service/config/RedisConfig.java`
- `showtime-service/src/main/java/com/cinema/showtime_service/config/RedisConfig.java`
- `identity-service/src/main/resources/application.yaml`
- `user-service/src/main/resources/application.yaml`
- `film-service/src/main/resources/application.yaml`
- `showtime-service/src/main/resources/application.yaml`
- `identity-service/src/main/java/com/cinema/identity_service/config/JwtAuthenticationFilter.java`
- `identity-service/src/main/java/com/cinema/identity_service/services/impl/UserServiceImpl.java`
- `film-service/src/main/java/com/cinema/film_service/services/impl/FilmServiceImpl.java`
- `identity-service/pom.xml`
- `user-service/pom.xml`
- `film-service/pom.xml`
- `showtime-service/pom.xml`
- `compose.prod.yaml`

---

## 7) Cách tự kiểm tra Redis đang chạy tốt hay chưa

Checklist nhanh:
1. Kiểm tra service có kết nối Redis ổn định (log startup không lỗi auth/timeout).
2. Quan sát cache hit ratio của endpoint phim.
3. Test update phim -> đọc lại ngay -> dữ liệu phải mới (không stale).
4. Test login/logout -> key token xuất hiện rồi bị xóa đúng.
5. Theo dõi Redis memory + eviction count.
6. Theo dõi p95/p99 latency Redis command.

---

## 8) Những hiểu lầm phổ biến (để tránh)

1. "Đã dùng Redis thì lúc nào cũng nhanh hơn DB".
- Sai nếu cache miss nhiều hoặc serialize quá nặng.

2. "Pool càng lớn càng tốt".
- Sai. Pool quá lớn có thể làm Redis quá tải và tăng contention.

3. "TTL càng dài càng tốt".
- Sai. TTL dài dễ gây stale data.

4. "Eviction policy nào cũng được".
- Sai. Policy phải phù hợp loại key (cache vs token/session).

---

## 9) Trạng thái xác minh hiện tại

- Đã review tĩnh toàn bộ thay đổi liên quan Redis.
- Chưa chạy compile/test tự động trong máy hiện tại vì thiếu Maven (`mvn` not found).

---

## 10) Kết luận dễ nhớ

Nếu tóm gọn bằng 1 câu:

Các thay đổi này biến Redis từ mức "dùng được" sang mức "vận hành được kiểu production":
- có kiểm soát connection
- có timeout rõ ràng
- có namespace key
- có invalidate cache đúng
- có runtime policy rõ ràng
- có local fallback an toàn

Điều này giúp hệ thống ổn định hơn khi traffic tăng, và giúp team debug/vận hành dễ hơn rất nhiều.
