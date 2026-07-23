# CinemaStar Postman/Newman Smoke Tests

Bộ kiểm thử này chạy trực tiếp qua production gateway:

```text
https://cinema-api.duckdns.org
```

Mục tiêu là có minh chứng kiểm thử đủ rõ để đưa vào chương kiểm thử: Postman dùng để demo thủ công, Newman dùng để chạy tự động và xuất báo cáo HTML/JUnit.

## Cấu trúc

- `CinemaStar.postman_collection.json`: collection chính.
- `cinema-prod.postman_environment.json`: environment thật để chạy production, đã được ignore để tránh commit mật khẩu.
- `cinema-prod.example.postman_environment.json`: file mẫu không chứa mật khẩu thật.
- `reports/`: nơi Newman xuất `report.html` và `junit.xml`.

## Cài công cụ

Chạy một lần trong thư mục này:

```powershell
cd C:\hoctap\Study\KLTN\CinemaStar\cinema-microservices\tools\postman
npm install
```

## Chạy read-only smoke test

Đây là lệnh nên dùng khi chuẩn bị báo cáo vì không tạo thanh toán MoMo và không sửa dữ liệu nghiệp vụ:

```powershell
npm run test:api:prod:readonly
```

Kết quả sẽ nằm ở:

```text
tools/postman/reports/report.html
tools/postman/reports/junit.xml
```

Mặc định `staffEnabled=false` vì credential `staff@gmail.com / 12345678aA@` đang bị production trả `4007 - Tên đăng nhập hoặc mật khẩu không chính xác`. Sau khi tài khoản staff được sửa/tạo lại, đổi `staffEnabled=true` trong environment để chạy thêm smoke test quyền staff.

## Chạy toàn bộ smoke test

Lệnh này chạy collection đầy đủ. Nhóm mutation vẫn bị khóa nếu `RUN_MUTATIONS=false`:

```powershell
npm run test:api:prod
```

## Chạy mutation smoke test

Chỉ dùng khi cần minh chứng create/update/delete. Nhóm này tạo review test có prefix `NEWMAN_KLTN`, update chính review đó, rồi xóa sau test.

```powershell
npm run test:api:prod:mutations
```

Điều kiện: tài khoản `customer@gmail.com` phải đủ điều kiện review phim được chọn, tức là đã mua vé, thanh toán thành công và suất chiếu đã kết thúc. Nếu không đủ điều kiện, mutation test sẽ fail đúng theo nghiệp vụ.

## Cách dùng trong Postman UI

1. Import `CinemaStar.postman_collection.json`.
2. Import `cinema-prod.postman_environment.json`.
3. Chọn environment `CinemaStar Production`.
4. Chạy folder `ReadOnly Smoke` bằng Collection Runner.
5. Chụp màn hình kết quả Runner và một vài response tiêu biểu để đưa vào luận văn.

## API được kiểm thử

- Auth: login admin, manager, staff, customer.
- Catalog: search/detail phim, search/detail suất chiếu, seat map, review search.
- Booking/Payment: booking history, active bookings, payment session nếu có booking.
- Reports: revenue theo rạp, revenue theo phim, hiệu suất suất chiếu.
- Export: kiểm tra Excel export trả file attachment không rỗng.

## Ghi chú vận hành

- Collection dùng cookie `accessToken`/`refreshToken` do `/api/auth/login` trả về; không tự set `X-User-ID` hay `X-User-Role` vì gateway chịu trách nhiệm xác thực và truyền header nội bộ.
- Các request theo role được sắp xếp để login đúng role ngay trước nhóm API cần quyền đó.
- Không hard-code ID dữ liệu production: collection tự search phim/suất chiếu trước rồi lưu `filmId`, `showtimeId`, `cinemaId`.
- Nếu production không có dữ liệu phim hoặc suất chiếu, các test capture ID sẽ fail để báo rõ môi trường thiếu dữ liệu kiểm thử.
