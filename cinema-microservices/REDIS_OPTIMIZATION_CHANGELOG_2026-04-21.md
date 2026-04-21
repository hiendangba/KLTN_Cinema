# Nhật ký tối ưu Redis và lý do kỹ thuật

Ngày: 2026-04-21  
Phạm vi: Các thay đổi liên quan Redis trong toàn bộ project  
Người thực hiện: Codex assistant

## 1) Mục tiêu của tài liệu này

Bạn yêu cầu không chỉ ghi lại các thay đổi mà còn giải thích thật kỹ "vì sao" phải đổi như vậy.  
Tài liệu này trả lời 5 câu hỏi cho từng nhóm thay đổi:
1. Đã đổi cái gì
2. Vì sao cần đổi
3. Rủi ro nào được giảm
4. Trade-off (đánh đổi) là gì
5. Sau khi đổi cần theo dõi gì

## 2) Những vấn đề ban đầu trước khi tối ưu

Qua review Redis toàn bộ project, các điểm chính trước khi chỉnh:
1. Trong `application.yaml` đã có cấu hình pool, nhưng cấu hình Java custom chưa theo hướng pooling rõ ràng như các hệ thống production lớn.
2. Timeout đang thiên về kiểu số thô, kém linh hoạt khi deploy nhiều môi trường.
3. Có chỗ chờ tài nguyên theo kiểu có thể kéo dài không mong muốn khi Redis nghẽn.
4. Namespace key Redis của identity (OTP/token) chưa đồng bộ triệt để.
5. Cache phim có nguy cơ stale data sau update/delete.
6. Redis trong `compose.prod.yaml` chưa có bộ cấu hình runtime đầy đủ theo hướng production (healthcheck, policy bộ nhớ, persistence rõ ràng).
7. Local dev có thể kém ổn định khi thiếu biến môi trường Redis.

## 3) Các quyết định kỹ thuật và lý do chi tiết

### Quyết định A: Dùng cấu hình Lettuce Pooling tường minh

File đã đổi:
- `identity-service/src/main/java/com/cinema/identity_service/config/RedisConfig.java`
- `user-service/src/main/java/com/cinema/user_service/config/RedisConfig.java`
- `film-service/src/main/java/com/cinema/film_service/config/RedisConfig.java`
- `showtime-service/src/main/java/com/cinema/showtime_service/config/RedisConfig.java`

Đã thay đổi:
- Chuyển sang `LettucePoolingClientConfiguration` + `GenericObjectPoolConfig`.
- Ánh xạ pool từ config (`max-active`, `max-idle`, `min-idle`, `max-wait`).
- Giữ các option phục hồi kết nối (`autoReconnect`, `pingBeforeActivateConnection`, `REJECT_COMMANDS` khi mất kết nối).

Vì sao phải đổi:
- Ở tải cao, quản lý vòng đời connection quyết định trực tiếp p95/p99 latency.
- Hệ thống production lớn thường cần hành vi pool “deterministic” thay vì phụ thuộc mặc định.
- Tránh connection churn khi có burst traffic.

Rủi ro giảm được:
- Tắc nghẽn thread khi kết nối Redis không ổn định.
- Dao động latency lớn do tạo/hủy kết nối liên tục.

Đánh đổi:
- Cần theo dõi metric pool để tuning định kỳ.
- Cấu hình pool sai vẫn có thể tạo bottleneck.


### Quyết định B: Bổ sung `commons-pool2`

File đã đổi:
- `identity-service/pom.xml`
- `user-service/pom.xml`
- `film-service/pom.xml`
- `showtime-service/pom.xml`

Đã thay đổi:
- Thêm dependency `org.apache.commons:commons-pool2`.

Vì sao phải đổi:
- Pooling của Lettuce cần backend pool implementation.
- Đây là dependency chuẩn cho cấu hình pool kiểu này.

Rủi ro giảm được:
- Tránh lỗi runtime hoặc pool không hoạt động đúng kỳ vọng.

Đánh đổi:
- Tăng thêm 1 dependency (nhẹ và phổ biến).


### Quyết định C: Chuẩn hóa timeout sang `Duration`

File đã đổi:
- 4 file `RedisConfig.java` ở trên.

Đã thay đổi:
- Các timeout dùng `Duration` với default rõ đơn vị: `5000ms`, `2000ms`, `100ms`.
- Áp dụng cho connect-timeout, command-timeout, pool max-wait, shutdown-timeout.

Vì sao phải đổi:
- Môi trường production thường set env theo dạng `2s`, `500ms`, `1m`.
- `Duration` giảm sai sót do nhầm đơn vị ms/s.

Rủi ro giảm được:
- Timeout quá ngắn hoặc quá dài do parse sai ý nghĩa giá trị.

Đánh đổi:
- Cần giữ format env nhất quán theo chuẩn Duration.


### Quyết định D: Ổn định serializer Redis theo hướng an toàn liên service

File đã đổi:
- 4 file `RedisConfig.java`.

Đã thay đổi:
- Dùng `GenericJackson2JsonRedisSerializer` với `JavaTimeModule`.
- Tắt format timestamp cho date/time.

Vì sao phải đổi:
- Dữ liệu cache/value cần serializable ổn định khi model thay đổi theo thời gian.
- Xử lý tốt kiểu thời gian của Java.

Rủi ro giảm được:
- Lỗi deserialize khó debug khi object/kiểu thời gian thay đổi.

Đánh đổi:
- JSON có thể lớn hơn một số binary serializer.


### Quyết định E: Namespace cache key theo tên service

File đã đổi:
- 4 file `RedisConfig.java`.

Đã thay đổi:
- Prefix cache theo mẫu: `appName::cacheName::...`

Vì sao phải đổi:
- Multi-service dùng chung Redis rất dễ đụng key nếu không namespace.
- Đây là thực hành phổ biến ở production lớn.

Rủi ro giảm được:
- Ghi đè cache giữa các service.
- Bug stale data khó truy vết.

Đánh đổi:
- Key dài hơn một chút (tăng nhẹ memory).


### Quyết định F: Sửa stale cache ở film-service

File đã đổi:
- `film-service/src/main/java/com/cinema/film_service/services/impl/FilmServiceImpl.java`

Đã thay đổi:
- Bổ sung `@CacheEvict(value = "films", key = "#id")` cho `updateFilm` và `deleteFilm`.

Vì sao phải đổi:
- Có `@Cacheable` ở luồng đọc thì luồng ghi bắt buộc phải evict/invalidate.
- Nếu không, API có thể trả dữ liệu cũ sau khi DB đã đổi.

Rủi ro giảm được:
- Sai lệch dữ liệu giữa DB và response API.

Đánh đổi:
- Sau thao tác ghi sẽ có một vài cache miss tự nhiên.


### Quyết định G: Chuẩn hóa keyspace token/OTP của identity

File đã đổi:
- `identity-service/src/main/java/com/cinema/identity_service/config/JwtAuthenticationFilter.java`
- `identity-service/src/main/java/com/cinema/identity_service/services/impl/UserServiceImpl.java`

Đã thay đổi:
- Đồng bộ prefix:
  - `identity:otp:`
  - `identity:otp:subject:`
  - `identity:token:access:`
  - `identity:token:refresh:`
  - `identity:user_tokens:`
- Giá trị user id trong token key lưu thống nhất kiểu string.
- Xóa token key theo batch trong luồng logout.

Vì sao phải đổi:
- Dễ vận hành, dễ quan sát key, dễ cô lập theo domain identity.
- Batch delete giảm số round-trip đến Redis.

Rủi ro giảm được:
- Va chạm key với module khác.
- Cleanup chậm khi người dùng có nhiều token.

Đánh đổi:
- Code cleanup phức tạp hơn một chút.


### Quyết định H: Cấu hình local an toàn hơn, production rõ ràng hơn

File đã đổi:
- `identity-service/src/main/resources/application.yaml`
- `user-service/src/main/resources/application.yaml`
- `film-service/src/main/resources/application.yaml`
- `showtime-service/src/main/resources/application.yaml`

Đã thay đổi:
- Thêm/chuẩn hóa:
  - `spring.data.redis.connect-timeout`
  - `spring.data.redis.client-name`
- Bổ sung default local:
  - host: `localhost`
  - port: `6379`
  - username/password: rỗng
- Đổi `max-wait` fallback sang hữu hạn (`2000`) thay vì vô hạn.

Vì sao phải đổi:
- Local cần chạy được ngay cả khi thiếu một số env.
- Production cần fail-fast có kiểm soát khi Redis bão hòa.

Rủi ro giảm được:
- Dev local lỗi khởi động do thiếu env Redis.
- Thread chờ vô hạn gây nghẽn chuỗi request.

Đánh đổi:
- Khi Redis rất tải nặng, request có thể fail sớm thay vì chờ lâu.


### Quyết định I: Hardening Redis trong `compose.prod.yaml`

File đã đổi:
- `compose.prod.yaml`

Đã thay đổi:
- Bổ sung command runtime cho Redis:
  - AOF bật
  - save snapshot rules
  - `maxmemory` + `maxmemory-policy allkeys-lru`
  - `tcp-keepalive`
- Bổ sung `healthcheck` (`redis-cli ping`).

Vì sao phải đổi:
- Production cần chính sách persistence/eviction rõ ràng.
- Healthcheck giúp orchestration giám sát readiness tốt hơn.

Rủi ro giảm được:
- Hành vi eviction khó đoán khi memory đầy.
- Trạng thái service mập mờ khi phụ thuộc Redis.

Đánh đổi:
- Persistence tăng overhead ghi.
- Nếu memory sizing không đủ, `allkeys-lru` có thể đẩy cả key quan trọng.

## 4) Danh sách file đã thay đổi (liên quan Redis)

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

## 5) Checklist vận hành sau khi triển khai

Nên theo dõi:
1. Pool metrics: active/idle/wait time.
2. Redis latency p95/p99.
3. Cache hit ratio (đặc biệt nhóm `films` và token-related).
4. Memory used + eviction count.
5. Số lượng key OTP/token theo thời gian.

Nếu p99 tăng:
1. Tăng `max-active` có kiểm soát.
2. Kiểm tra lại `command-timeout` và `max-wait`.
3. Kiểm tra sizing Redis + network latency giữa service và Redis.

## 6) Trạng thái kiểm chứng

- Đã review tĩnh toàn bộ thay đổi và tính nhất quán cấu hình Redis.
- Chưa chạy compile/test trực tiếp trong môi trường hiện tại vì thiếu Maven (`mvn` not found).

## 7) Kết luận ngắn

Sau các thay đổi này, Redis setup của project đã gần hơn với cách làm ở các hệ thống production lớn:
- Pooling tường minh và có giới hạn chờ
- Timeout typed bằng `Duration`
- Namespace key rõ ràng theo service/domain
- Invalidate cache đúng luồng ghi
- Runtime Redis có healthcheck + memory/persistence policy
- Local có default an toàn để giảm lỗi môi trường

Điểm quan trọng nhất: hệ thống giờ có hành vi ổn định và dự đoán được hơn khi tải tăng hoặc khi Redis gặp dao động.
