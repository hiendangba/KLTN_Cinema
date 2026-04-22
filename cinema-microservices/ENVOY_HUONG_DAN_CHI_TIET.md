# Hướng Dẫn Sử Dụng Envoy Chi Tiết (Cho Dự Án Cinema Microservices)

## 1. Envoy là gì

Envoy trong dự án của bạn là API Gateway đứng trước toàn bộ microservice.

Envoy làm 5 việc chính:
- Nhận request từ frontend/client.
- Chọn service đích theo path (`/api/films`, `/api/halls`, ...).
- Kiểm tra auth tập trung qua `ext_authz` (gọi sang `identity-service`).
- Thêm/điều phối header phục vụ phân quyền nội bộ.
- Ghi log và cung cấp admin endpoint để debug.

## 2. File liên quan trong repo

- Local: `envoy/envoy.local.yaml`
- Production: `envoy/envoy.prod.yaml`
- Chạy prod bằng Docker Compose: `compose.prod.yaml` (service `envoy`)
- Overlay local: `compose.local.yaml`

## 3. Kiến trúc luồng request

1. Client gọi vào domain gateway.
2. Envoy match route theo path.
3. Nếu route không phải `/api/auth/**`, Envoy chạy `ext_authz`.
4. `identity-service` trả allow/deny.
5. Allow: Envoy forward request vào service đích.
6. Deny: trả về `401` (thường thấy mã log `UAEX`).

## 4. Cấu trúc chính trong file Envoy

- `listeners`: cổng Envoy lắng nghe (`80`, `443`).
- `http_connection_manager`: phần xử lý HTTP chính.
- `http_filters`: bộ lọc theo thứ tự (`cors` -> `ext_authz` -> `router`).
- `route_config`: map path -> cluster.
- `clusters`: định nghĩa backend nội bộ.
- `admin`: cổng quản trị/debug (`9901`).

## 5. Ý nghĩa các command quan trọng (kèm cách dùng)

## 5.1 Xem log Envoy realtime

```bash
docker logs -f api-gateway
```

Ý nghĩa:
- `docker logs`: xem stdout/stderr của container.
- `-f` (`--follow`): bám theo log realtime.
- `api-gateway`: tên container Envoy trong `compose.prod.yaml`.

Dùng khi nào:
- Vừa deploy xong Envoy và muốn kiểm tra có lỗi parse config không.
- Debug nhanh lỗi 401/404/503.

Kỳ vọng:
- Thấy access log từng request và thông tin upstream.

## 5.2 Xem nhanh 200 dòng log gần nhất

```bash
docker logs --tail 200 api-gateway
```

Ý nghĩa:
- `--tail 200`: chỉ lấy 200 dòng cuối, đỡ ngập log.

Dùng khi nào:
- Cần check nhanh sự cố vừa xảy ra.

## 5.3 Restart Envoy sau khi sửa config

```bash
docker compose -f compose.prod.yaml up -d envoy
```

Ý nghĩa tham số:
- `docker compose`: chạy stack compose.
- `-f compose.prod.yaml`: dùng file compose production.
- `up`: tạo/chạy container theo config mới.
- `-d`: chạy nền.
- `envoy`: chỉ restart service Envoy, không động vào service khác.

Dùng khi nào:
- Bạn vừa sửa `envoy/envoy.prod.yaml` hoặc volume cert.

## 5.4 Kéo image Envoy mới trước khi up

```bash
docker compose -f compose.prod.yaml pull envoy
```

Ý nghĩa:
- `pull envoy`: kéo image mới nhất của service Envoy theo tag đang dùng.

Dùng khi nào:
- Bạn đổi version image hoặc muốn chắc chắn dùng bản mới.

## 5.5 Kiểm tra Envoy đang chạy hay chưa

```bash
docker ps --filter "name=api-gateway"
```

Ý nghĩa:
- Lọc danh sách container theo tên chứa `api-gateway`.

Kỳ vọng:
- Có container trạng thái `Up`.

## 5.6 Vào shell của container Envoy

```bash
docker exec -it api-gateway /bin/sh
```

Ý nghĩa:
- `exec`: chạy lệnh trong container đang sống.
- `-it`: interactive terminal.
- `/bin/sh`: mở shell.

Dùng khi nào:
- Cần xem file config đã mount vào container chưa.

Ví dụ sau khi vào container:
```bash
cat /etc/envoy/envoy.yaml
```

## 5.7 Xem stats của Envoy từ admin port

```bash
curl http://localhost:9901/stats
```

Ý nghĩa:
- Query admin API của Envoy để xem counters/metrics.

Dùng khi nào:
- Muốn biết filter nào đang fail nhiều (ext_authz, upstream reset, ...).

## 5.8 Xem toàn bộ config Envoy đang nạp thực tế

```bash
curl http://localhost:9901/config_dump
```

Ý nghĩa:
- Trả về cấu hình runtime hiện tại mà Envoy đang dùng.

Dùng khi nào:
- Nghi ngờ Envoy chưa ăn config mới.

## 5.9 Kiểm tra tình trạng cluster/upstream

```bash
curl http://localhost:9901/clusters
```

Ý nghĩa:
- Xem health/connection info của từng cluster (`film-service`, `hall-services`, ...).

Dùng khi nào:
- Lỗi `503 UF` hoặc nghi backend không reachable.

## 5.10 Test route qua gateway bằng curl

```bash
curl -i https://cinema-api.duckdns.org/api/films/search \
  -H "Content-Type: application/json" \
  --data '{"size":10}'
```

Ý nghĩa:
- `-i`: in cả response headers.
- `-H`: thêm HTTP header.
- `--data`: body cho POST.

Dùng khi nào:
- Test nhanh route và status code từ gateway.

## 5.11 Test route có cookie auth

```bash
curl -i https://cinema-api.duckdns.org/api/halls/<HALL_ID> \
  -H "Cookie: accessToken=<JWT>"
```

Ý nghĩa:
- Giả lập request browser có gửi cookie auth.

Dùng khi nào:
- Debug `401 UAEX` liên quan cookie/token.

## 5.12 Lọc log theo mã lỗi (Linux VPS)

```bash
docker logs api-gateway 2>&1 | grep " 401 "
```

Ý nghĩa:
- `2>&1`: gộp stderr vào stdout.
- `grep " 401 "`: lọc dòng chứa status 401.

Dùng khi nào:
- Muốn thống kê nhanh lỗi auth.

## 5.13 Lọc log theo mã lỗi (PowerShell Windows)

```powershell
docker logs api-gateway 2>&1 | Select-String " 401 "
```

Ý nghĩa:
- `Select-String` là bản tương đương `grep` trên PowerShell.

## 6. Cách đọc access log Envoy nhanh cho người mới

Ví dụ log:
```text
"POST /api/films/search HTTP/1.1" 401 UAEX ...
```

Đọc như sau:
- `POST /api/films/search`: request path.
- `401`: status trả về client.
- `UAEX`: request bị từ chối bởi external auth (`ext_authz`).

## 7. Lỗi thường gặp và câu lệnh xử lý

## 7.1 Lỗi 401 UAEX

Nguyên nhân hay gặp:
- Không gửi cookie/token.
- Token hết hạn hoặc bị revoke trong Redis.
- Cookie policy sai (`SameSite`, `Secure`, `credentials`).

Câu lệnh nên chạy:
```bash
docker logs --tail 200 api-gateway
docker logs --tail 200 cinema-deploy-identity-service-1
curl http://localhost:9901/stats | grep ext_authz
```

## 7.2 Lỗi 404 NR (No Route)

Nguyên nhân hay gặp:
- Path không match route config.
- Thiếu route trong `envoy.prod.yaml`.

Câu lệnh nên chạy:
```bash
curl http://localhost:9901/config_dump
```

## 7.3 Lỗi 503 UF (upstream failure)

Nguyên nhân hay gặp:
- Backend service down.
- Sai host/port cluster.

Câu lệnh nên chạy:
```bash
curl http://localhost:9901/clusters
docker ps
```

## 8. Quy trình chuẩn khi bạn sửa Envoy

1. Sửa file `envoy/envoy.prod.yaml`.
2. Restart Envoy:
```bash
docker compose -f compose.prod.yaml up -d envoy
```
3. Check container:
```bash
docker ps --filter "name=api-gateway"
```
4. Check log:
```bash
docker logs --tail 100 api-gateway
```
5. Test endpoint thật bằng `curl`.
6. Nếu ổn, theo dõi log thêm 10-15 phút.

## 9. Ghi nhớ quan trọng cho dự án của bạn

- Route `/api/auth/**` đang tắt `ext_authz` để login/refresh hoạt động.
- Các route còn lại đi qua `ext_authz`, nên lỗi auth thường nằm ở cookie/token hoặc identity-service.
- Tên service nội bộ phải đồng bộ giữa `routes`, `clusters`, `compose` (ví dụ `hall-services`).
- Khi đổi cookie policy, luôn test lại qua Envoy, không test riêng service.

## 10. Bản tóm tắt 5 lệnh bạn sẽ dùng nhiều nhất

```bash
docker compose -f compose.prod.yaml up -d envoy
docker logs -f api-gateway
curl http://localhost:9901/stats
curl http://localhost:9901/config_dump
curl http://localhost:9901/clusters
```

Chỉ cần nắm chắc 5 lệnh này, bạn đã debug được phần lớn sự cố Envoy trong hệ thống hiện tại.
