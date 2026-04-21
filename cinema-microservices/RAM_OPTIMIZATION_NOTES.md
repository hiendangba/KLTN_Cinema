# Ghi Chú Tối Ưu RAM Cho Hệ Thống Microservices

## 1) Bối cảnh và mục tiêu

Bạn đang chạy nhiều service trên VPS RAM nhỏ (tổng khoảng 1.9 GiB). Trước tối ưu, các service Java chiếm RAM cao, dẫn đến:
- Không còn đủ RAM để bật thêm service mới.
- Dễ gặp rủi ro OOM khi có tải tăng nhẹ.
- Khó vận hành vì không có ngân sách RAM rõ ràng theo từng service.

Mục tiêu của đợt này:
- Giữ kiến trúc microservice hiện tại (không gộp service).
- Tối ưu sâu ở mức cấu hình/runtime.
- Giảm RAM thực dùng để còn dư địa mở rộng.

Kết quả thực tế bạn báo lại:
- `Mem total: 1.9Gi`
- `used: 863Mi`
- `free: 617Mi`
- `available: 907Mi`

=> Có thể xem là đã giảm rất mạnh mức tiêu thụ RAM tổng, đúng mục tiêu “còn chỗ để chạy thêm service”.

## 2) Những thay đổi đã thực hiện (chi tiết)

## 2.1) Tối ưu JVM theo từng service trong `compose.yaml`

Đã chỉnh `JAVA_OPTS` theo hướng giảm footprint bộ nhớ:
- Giảm `-Xms` để lúc khởi động không “ôm” heap quá sớm.
- Đặt `-Xmx` theo vai trò service thay vì để rộng tay đồng loạt.
- Dùng `-XX:+UseSerialGC` (phù hợp workload vừa/nhỏ, footprint thấp).
- Giới hạn `Metaspace` và `CodeCache` để tránh JVM phình không kiểm soát.
- Bật `-XX:+ExitOnOutOfMemoryError` để fail-fast, tránh trạng thái treo khó chẩn đoán.

Đồng thời hạ `mem_limit` cho từng container để:
- Tránh 1 service “ăn lấn” RAM của toàn cụm.
- Tạo ngân sách RAM rõ ràng, dễ giám sát.

Ghi chú sự cố đã gặp:
- Có phát sinh `OutOfMemoryError: Metaspace` ở `hall-services` khi siết quá tay.
- Đã xử lý bằng cách tăng lại `MaxMetaspaceSize` lên mức an toàn hơn (đặc biệt cho hall/film/showtime và các service chính).

## 2.2) Giảm Hikari pool (kết nối PostgreSQL)

Trong các service có DB, đã thêm/siết:
- `spring.datasource.hikari.maximum-pool-size`
- `spring.datasource.hikari.minimum-idle`
- `idle-timeout`
- `max-lifetime`
- `connection-timeout`

Hướng cấu hình:
- Pool nhỏ, đủ dùng theo tải thực tế.
- Không giữ quá nhiều kết nối idle.

Lợi ích:
- Giảm số thread và object liên quan đến pool.
- Giảm RAM trong JVM.
- Giảm áp lực kết nối lên PostgreSQL.

## 2.3) Giảm Redis Lettuce pool mặc định

Đã giảm mặc định:
- `max-active`: 20 -> 8
- `max-idle`: 10 -> 4
- `min-idle`: 5 -> 1

Lợi ích:
- Giảm số kết nối/đối tượng socket.
- Giảm tài nguyên nền mà vẫn đủ cho hầu hết tải thông thường.

## 2.4) Tắt log SQL nặng và cấu hình JPA tốn tài nguyên

Đã chỉnh:
- `spring.jpa.show-sql=false` (mặc định)
- `hibernate.format_sql=false`
- Gỡ log mức nặng ở showtime:
  - `org.hibernate.SQL: DEBUG`
  - `org.hibernate.orm.jdbc.bind: TRACE`

Vì sao giúp giảm RAM:
- Log SQL chi tiết tạo rất nhiều chuỗi (string), buffer, và I/O.
- Ở tải cao, phần log có thể làm tăng CPU và RAM đáng kể.

## 2.5) Tắt Open Session In View

Đã cấu hình:
- `spring.jpa.open-in-view=false`

Áp dụng ở:
- `hall-service`
- `film-service`
- `showtime-service`

Ý nghĩa:
- Không giữ persistence context kéo dài qua toàn request/view.
- Giảm nguy cơ query phát sinh ngoài ý muốn và giảm overhead session.

## 2.6) Tắt thành phần không cần cho runtime production

Đã set trong compose:
- `SPRINGDOC_API_DOCS_ENABLED=false`
- `SPRINGDOC_SWAGGER_UI_ENABLED=false`

Mục tiêu:
- Tắt bớt endpoint/bean không phục vụ luồng runtime chính trên VPS.

## 2.7) Dọn dependency thừa/không cần ở runtime

Đã dọn:
- Bỏ `spring-boot-devtools` khỏi `user-service` (không cần trong production).
- Bỏ dependency trùng lặp `spring-rabbit` ở `email-service` (đã có trong starter AMQP).
- Bỏ một số dependency không cần nằm ở runtime dependencies (giữ đúng chỗ compile-time/annotation processor).

Lợi ích:
- Classpath gọn hơn.
- Startup và footprint bộ nhớ ổn định hơn.

## 2.8) Thêm công cụ theo dõi service ngốn RAM

Đã thêm script:
- `scripts/docker-memory-watch.ps1`

Script này:
- Đọc `docker stats --no-stream`.
- Parse và sắp xếp theo `% RAM` giảm dần.
- Hiển thị top service ngốn RAM nhất.

Lệnh dùng:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\docker-memory-watch.ps1 -Top 10
```

Theo dõi liên tục:
```powershell
while ($true) { Clear-Host; .\scripts\docker-memory-watch.ps1 -Top 10; Start-Sleep 3 }
```

## 3) Vì sao thiết kế/cấu hình trước đây chưa tốt cho VPS RAM nhỏ

Các điểm chính:
- Cấp heap và memory limit khá cao cho nhiều service cùng lúc, chưa có “ngân sách RAM” rõ ràng theo vai trò.
- Pool DB/Redis để mặc định lớn hơn nhu cầu thực tế.
- Để log SQL chi tiết trong môi trường chạy thật.
- Có dependency/dev feature không cần cho production.
- Chưa có quy trình theo dõi service “nặng nhất” theo thời gian để phản ứng sớm.

Tóm lại:
- Vấn đề không phải do microservice là sai.
- Vấn đề nằm ở cấu hình runtime chưa “fit” với hạ tầng RAM nhỏ.

## 4) Bài học kỹ thuật rút ra

1. Mỗi service phải có ngân sách RAM riêng:
- `Xms`, `Xmx`, `MaxMetaspaceSize`, `CodeCache`, `mem_limit`.

2. Pool phải đi theo tải thật:
- Không dùng default lớn nếu chưa cần.

3. Log debug chỉ bật khi điều tra:
- Không để `DEBUG/TRACE` SQL ở trạng thái mặc định.

4. Tách rõ dev và prod:
- OpenAPI/Swagger/actuator/debug cần có profile.

5. Giám sát là bắt buộc:
- Có script và quy trình kiểm tra top memory định kỳ.

## 5) Checklist vận hành trước khi deploy (đề xuất)

1. Kiểm tra JVM flags từng service:
- Có `Xmx` rõ ràng chưa?
- Metaspace có quá thấp gây lỗi hay quá cao gây lãng phí không?

2. Kiểm tra pool:
- Hikari max/min có hợp với throughput hiện tại không?
- Redis pool có vượt nhu cầu không?

3. Kiểm tra log level:
- Có service nào còn `DEBUG/TRACE` ngoài ý muốn không?

4. Kiểm tra feature prod:
- Có bật swagger/api-docs ở môi trường production không?
- Có bật devtools hoặc tính năng dev không cần thiết không?

5. Kiểm tra container budget:
- `mem_limit` có vượt ngân sách tổng của VPS không?

6. Kiểm tra sau deploy:
- Chạy script top RAM.
- So sánh trước/sau trong 15-30 phút để đảm bảo ổn định.

## 6) Hướng cải thiện tiếp theo (nếu cần giảm RAM thêm)

1. Tách profile `prod` trong từng service:
- Gom toàn bộ cấu hình tiết kiệm RAM vào profile rõ ràng.

2. Tối ưu thêm theo traffic thật:
- Service nào ít request có thể giảm tiếp `Xmx` và pool.
- Service nào nóng thì giữ biên an toàn cao hơn để tránh restart do OOM.

3. Thiết lập cảnh báo sớm:
- Alert khi container vượt ngưỡng RAM (ví dụ 80% trong 5 phút).

4. Đánh giá lại phân bố chức năng:
- Nếu có service quá nhẹ, có thể gom domain hợp lý trong tương lai (chỉ khi thật sự cần), nhưng hiện tại vẫn giữ microservice theo yêu cầu.

## 7) Danh sách tệp đã chỉnh trong đợt này

- `compose.yaml`
- `identity-service/src/main/resources/application.yaml`
- `user-service/src/main/resources/application.yaml`
- `film-service/src/main/resources/application.yaml`
- `showtime-service/src/main/resources/application.yaml`
- `hall-service/src/main/resources/application.yaml`
- `user-service/pom.xml`
- `identity-service/pom.xml`
- `film-service/pom.xml`
- `showtime-service/pom.xml`
- `hall-service/pom.xml`
- `email-service/pom.xml`
- `scripts/docker-memory-watch.ps1`

---

Nếu cần, có thể tạo thêm một tài liệu riêng dạng “SOP tối ưu RAM cho dự án” (ngắn hơn, theo checklist thao tác nhanh) để team dùng mỗi lần rollout.


## 8) Quick Executive Summary (Added 2026-04-21)

### Overall status
- The RAM optimization pass achieved the main goal: keep the microservice architecture and significantly lower memory usage.
- On a 1.9 GiB VPS, post-optimization numbers indicate enough headroom to run additional services more safely.
- The chosen approach is valid: optimize runtime budgets per service instead of changing architecture.

### Key outcomes
- Rebalanced JVM budgets per service (`Xms`, `Xmx`, metaspace, code cache).
- Reduced DB and Redis pool sizes to match real traffic.
- Disabled heavy SQL logging and non-essential production runtime components.
- Removed unnecessary dependencies to reduce classpath and runtime footprint.
- Added Docker memory watch script for continuous visibility.

### What to monitor next
- Track memory behavior during peak windows for at least 7 days.
- Re-tune services that frequently exceed 80% of `mem_limit`.
- Keep `DEBUG/TRACE` off by default outside active investigations.

### Short operations guidance
1. Keep current settings for dev/UAT unless clear bottlenecks appear.
2. Scale `Xmx` and pool sizes only after measuring real load.
3. Before every deploy, run the memory checklist and observe 15-30 minutes after rollout.

### Done criteria for this optimization cycle
- No OOM-triggered service restarts during observation.
- Total RAM usage stays within the agreed safety threshold.
- Team can quickly identify top memory consumers using the monitoring script.
