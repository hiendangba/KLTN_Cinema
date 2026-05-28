# Quy tắc Báo cáo Doanh thu

## Mục tiêu

- Tạo 2 API báo cáo doanh thu theo rạp:
  - `POST /api/payments/revenues/cinemas/search` cho `ADMIN`
  - `POST /api/payments/revenues/cinemas/me/search` cho `MANAGER`
- Báo cáo phải:
  - phân trang danh sách rạp bằng `PageRequest`
  - có `page` và `total` summary riêng
  - lọc theo `dateRange`
  - tính theo **lúc thực thu / hoàn tiền thực tế**

## Vì sao chọn `payment-service`

- `Booking` phù hợp cho doanh thu bán hàng gộp:
  - vé
  - sản phẩm
  - tổng đơn ban đầu
- `PaymentTransaction` phù hợp hơn cho báo cáo tiền thực thu vì nó có:
  - `paidAt`
  - `refundedAt`
  - `cinemaId`
  - `amount`
  - snapshot `ticketSubtotal` / `productSubtotal`
- Nếu hoàn tiền xuất hiện sau này, báo cáo vẫn đúng nếu dựa vào payment event thay vì chỉ dựa vào giá trị đơn gốc.

## Nguồn dữ liệu

- `showtime` là nguồn sự thật của `cinemaId`
- `booking` snapshot lại:
  - `cinemaId`
  - `ticketSubtotal`
  - `productSubtotal`
  - `finalAmount`
- `payment_transaction` snapshot tiếp:
  - `cinemaId`
  - `ticketSubtotalSnapshot`
  - `productSubtotalSnapshot`
  - `amount`
- Report không sử dụng `userId` để suy ra rạp.

## Định nghĩa thời điểm tính report

- `paidAt`:
  - cộng doanh thu vào report
- `refundedAt`:
  - trừ doanh thu ra report
- Không dùng `timeCreated` để tính doanh thu, vì đó là thời điểm tạo giao dịch, không phải thời điểm tiền thực sự vào/ra.

## Contract request

- `dateRange` gửi riêng, không nhét vào `PageRequest`
- `pageRequest` chỉ lo:
  - page
  - size
  - keyword
  - sort
  - filter

## Contract response

- `items`: danh sách rạp của trang hiện tại
- `page`: summary của trang hiện tại
- `total`: summary của toàn bộ scope lọc
- Metadata phân trang:
  - `currentPage`
  - `totalPages`
  - `totalElements`
  - `size`
  - `hasNext`
  - `hasPrevious`

## Cách tính summary

- Query DB chỉ lấy data theo từng `cinema_id`
- Summary `page` và `total` được cộng bằng Java
- Không dùng `GROUPING SETS`
- Lý do:
  - dễ đổi DB sau này
  - logic tổng hợp nằm rõ trong code

## Phạm vi truy cập

- `ADMIN`:
  - lấy toàn bộ rạp active
- `MANAGER`:
  - lấy các rạp mình quản lý
- Rạp không có doanh thu trong khoảng lọc vẫn phải hiện ra với số 0

## Bộ lọc phim

- Chưa làm trong phase này
- Sẽ làm sau khi API report này ổn định

## Các file đã đổi trong phase này

- `common-lib/src/main/java/com/cinema/dto/request/DateRange.java`
- `common-lib/src/main/proto/cinema_internal.proto`
- `cinema-service/src/main/java/com/cinema/cinema_service/grpc/CinemaInternalGrpcService.java`
- `cinema-service/src/main/java/com/cinema/cinema_service/services/impl/CinemaServiceImpl.java`
- `payment-service/src/main/java/com/cinema/payment_service/controller/PaymentController.java`
- `payment-service/src/main/java/com/cinema/payment_service/repository/PaymentTransactionRepositoryImpl.java`
- `payment-service/src/main/java/com/cinema/payment_service/services/impl/PaymentSessionServiceImpl.java`
- `TECHNICAL_AGENT_GUIDE.md`

## Lý do thiết kế này

- Cách này giữ được báo cáo doanh thu theo rạp mà vẫn có summary rõ ràng.
- Mẫu request/response không phá contract `PageRequest` hiện có.
- Báo cáo sửa theo event time (`paidAt`/`refundedAt`) nên phù hợp với nghiệp vụ thanh toán thực tế.
- Snapshot `cinemaId` từ `showtime -> booking -> payment` giúp report không phải join ngược theo `userId`.

