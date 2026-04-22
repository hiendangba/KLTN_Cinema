# Hướng Dẫn Sử Dụng Envoy Chi Tiết (Cho Dự Án Cinema Microservices)

## 1. Envoy là gì và vai trò trong dự án này

Trong hệ thống của bạn, Envoy đang đóng vai trò **API Gateway**:

- Nhận toàn bộ request từ client.
- Route request vào đúng service nội bộ (`identity`, `film`, `showtime`, `hall`, `cinema`, `email`, ...).
- Áp dụng CORS tập trung.
- Kiểm tra xác thực tập trung bằng `ext_authz` thông qua `identity-service`.
- Thêm header gateway (`x-gateway: envoy`) để truy vết.

Nói ngắn gọn: client chỉ nói chuyện với Envoy, Envoy mới nói chuyện với từng microservice.

## 2. File cấu hình quan trọng trong repo

- Cấu hình Envoy local: `envoy/envoy.local.yaml`
- Cấu hình Envoy production: `envoy/envoy.prod.yaml`
- Cấu hình chạy container production: `compose.prod.yaml` (service `envoy`)
- Cấu hình overlay local: `compose.local.yaml` (map local config cho Envoy)

## 3. Cách Envoy đang chạy ở local và production

## 3.1 Local

- Envoy chạy HTTP cổng `80`.
- Upstream trỏ tới `host.docker.internal:<port>` (service chạy từ máy host/IDE).
- Không có TLS termination.

## 3.2 Production

- Envoy mở cả `80` và `443`.
- Listener `80` chỉ dùng để redirect HTTPS.
- Listener `443` xử lý route thực tế và auth.
- TLS cert load từ:
  - `/etc/envoy/certs/fullchain.pem`
  - `/etc/envoy/certs/privkey.pem`

## 4. Giải phẫu một file Envoy config

## 4.1 `listeners`

Listener là cổng Envoy lắng nghe:

- `listener_http`:
  - local: xử lý request trực tiếp.
  - prod: chỉ redirect sang HTTPS.
- `listener_https` (prod):
  - nhận HTTPS thật, xử lý auth + routing.

## 4.2 `http_connection_manager`

Đây là phần điều khiển HTTP chính:

- `normalize_path: true`: chuẩn hóa path.
- `use_remote_address: true`: dùng IP client thật.
- `xff_num_trusted_hops: 1`: tin cậy 1 proxy hop phía trước.
- `access_log`: log ra stdout để đọc qua `docker logs`.

## 4.3 `http_filters`

Thứ tự filter rất quan trọng:

1. `cors`
2. `ext_authz`
3. `router`

Ý nghĩa:
- request đi qua CORS trước,
- rồi kiểm tra auth,
- cuối cùng mới route sang upstream.

## 4.4 `route_config`

`virtual_hosts -> routes` là luật map path -> cluster.

Ví dụ dự án của bạn:

- `/api/auth/**` -> `identity-service` (tắt `ext_authz` cho route này).
- `/api/films/**` -> `film-service`
- `/api/showtimes/**` -> `showtime-service`
- `/api/halls/**` -> `hall-services`
- `/api/cinemas/**` -> `cinema-service`

## 4.5 `clusters`

Mỗi cluster là một upstream service:

- `name`: tên dùng trong route.
- `connect_timeout`: timeout kết nối.
- `type: LOGICAL_DNS`: resolve theo DNS nội bộ Docker.
- `load_assignment`: địa chỉ/port thực tế.

## 4.6 `admin`

Admin interface đang ở cổng `9901` để debug:

- xem stats
- xem config dump
- xem runtime info

## 5. Cơ chế xác thực tập trung (`ext_authz`)

Envoy gọi:

- `identity-service/internal/auth/check`

với timeout `1s`.

Envoy chỉ forward một số header sang auth service:

- `authorization`
- `cookie`

Nếu auth thành công, Envoy cho phép thêm header lên upstream:

- `x-user-id`
- `x-user-role`

Nếu auth fail, client nhận `401` (log thường thấy `UAEX`).

## 6. Vì sao route `/api/auth/**` được miễn auth

Trong config có `typed_per_filter_config` cho route `/api/auth/`:

- `envoy.filters.http.ext_authz.disabled: true`

Mục đích:
- cho phép login/refresh/logout hoạt động,
- tránh vòng lặp “chưa có token nhưng bị chặn trước khi login”.

## 7. CORS và cookie trong hệ thống của bạn

Envoy đang bật:

- `allow_credentials: true`
- `allow_origin_string_match: .*`

Khi dùng cookie cross-site cho auth, cần đồng bộ:

1. FE gọi API với credentials (`withCredentials` hoặc `credentials: include`).
2. Cookie auth phải có `SameSite=None; Secure` trong production HTTPS.
3. Envoy đã cho phép header `cookie` qua `ext_authz`.

## 8. Các lệnh vận hành Envoy hữu ích

## 8.1 Xem log Envoy

```bash
docker logs -f api-gateway
```

## 8.2 Reload/restart Envoy khi sửa config

```bash
docker compose -f compose.prod.yaml up -d envoy
```

## 8.3 Xem stats từ admin port

```bash
curl http://localhost:9901/stats
```

## 8.4 Xem config dump Envoy đang nạp

```bash
curl http://localhost:9901/config_dump
```

## 8.5 Kiểm tra cluster/upstream state

```bash
curl http://localhost:9901/clusters
```

## 9. Hướng dẫn thêm route service mới

Ví dụ bạn thêm `booking-service` HTTP:

1. Thêm route trong `virtual_hosts.routes`:

```yaml
- match:
    prefix: /api/bookings/
  route:
    cluster: booking-service
    timeout: 30s
```

2. Thêm cluster:

```yaml
- name: booking-service
  connect_timeout: 1s
  type: LOGICAL_DNS
  lb_policy: ROUND_ROBIN
  load_assignment:
    cluster_name: booking-service
    endpoints:
      - lb_endpoints:
          - endpoint:
              address:
                socket_address:
                  address: booking-service
                  port_value: 809x
```

3. Đảm bảo service đó có trong `compose.prod.yaml`.
4. Restart Envoy.
5. Test route mới bằng `curl`.

## 10. Hướng dẫn debug lỗi thường gặp

## 10.1 `401 UAEX`

Ý nghĩa: Envoy deny ở `ext_authz`.

Checklist:

1. Cookie/access token có gửi lên chưa.
2. `identity-service` có đọc được token từ cookie/header chưa.
3. Redis token key còn sống không.
4. Cookie attributes đúng chưa (`SameSite=None`, `Secure=true` trên HTTPS).

## 10.2 `404 NR` (No Route)

Envoy không match route.

Checklist:

1. Prefix/path match đúng chưa.
2. Có nhầm `/api/halls` và `/api/halls/` không.
3. Đã deploy đúng file config (`local` vs `prod`) chưa.

## 10.3 `503 UF` hoặc upstream connection error

Envoy route được nhưng không kết nối được backend.

Checklist:

1. Tên cluster có đúng với route không.
2. Address/port có đúng service thực tế không.
3. Service backend có đang chạy không.
4. DNS nội bộ Docker resolve đúng không.

## 11. Quy trình an toàn khi sửa Envoy (khuyến nghị)

1. Sửa config trong branch.
2. Validate YAML.
3. Chạy local trước (`envoy.local.yaml`).
4. Test các luồng trọng yếu:
   - login
   - gọi API protected
   - CORS từ frontend
5. Deploy production.
6. Theo dõi `docker logs api-gateway` + `/stats` ít nhất 10-15 phút.

## 12. Kinh nghiệm thực chiến cho hệ thống của bạn

- Giữ timeout `ext_authz` thấp (1s) là hợp lý để fail-fast, nhưng cần đảm bảo `identity-service` ổn định.
- Chỉ nên miễn auth cho đúng route bắt buộc (`/api/auth/**`).
- Khi đổi cookie policy, luôn test lại qua Envoy vì đây là điểm quyết định có forward `cookie` sang auth check hay không.
- Đồng bộ naming giữa config (`hall-services`) và compose để tránh lỗi route/cluster mismatch.

---

Nếu bạn muốn, mình có thể viết tiếp bản “playbook theo từng sự cố” kiểu:
- “401 sau login”
- “route mới không ăn”
- “service timeout”
- “TLS lỗi chứng chỉ”

để bạn tra nhanh khi vận hành VPS.
