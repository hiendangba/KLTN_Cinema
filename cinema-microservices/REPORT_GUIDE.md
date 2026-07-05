# Report Guide

Tài liệu này giải thích ý nghĩa các API báo cáo hiện còn dùng trong CinemaStar.

Mục tiêu:
- Giúp developer, tester và giảng viên hiểu nhanh report nào dùng để làm gì.
- Phân biệt rõ report nhìn theo `payment`, report nhìn theo `booking`, report nhìn theo `showtime`.
- Chỉ mô tả các API report JSON đang dùng trong code, không tính endpoint export Excel nếu muốn nói riêng về thao tác tải file.

---

## 1. Payment Revenue Report

### API
- `POST /api/payments/revenues/cinemas/search`
- `POST /api/payments/revenues/cinemas/me/search`
- `POST /api/payments/revenues/cinemas/export`

### Ý nghĩa
Đây là report doanh thu tiền thực thu theo rạp.

Nó trả lời các câu hỏi:
- Rạp nào đang thu được bao nhiêu tiền?
- Trong khoảng thời gian lọc, có bao nhiêu giao dịch `PAID`, bao nhiêu giao dịch `REFUNDED`?
- Doanh thu thực tế còn lại là bao nhiêu sau hoàn tiền?

### Dữ liệu gốc
- `PaymentTransaction`
- Snapshot trong payment transaction:
  - `cinemaId`
  - `filmId`
  - `ticketSubtotalSnapshot`
  - `productSubtotalSnapshot`
  - `amount`
  - `paidAt`
  - `refundedAt`
  - promotion snapshot

### Dùng khi nào
- Khi admin muốn xem doanh thu toàn hệ thống theo rạp.
- Khi manager chỉ muốn xem rạp mình quản lý.
- Khi cần báo cáo tiền thật đã vào ví/cổng thanh toán, không phải giá trị booking dự kiến.

### Ví dụ thực tế
- Tháng này Cinema Star Lê Văn Việt đã thu bao nhiêu tiền từ MoMo?
- Trong khoảng từ 1/5 đến 31/5, có bao nhiêu giao dịch đã paid và bao nhiêu giao dịch đã refund?

### Câu dễ nhớ
- Payment revenue = “tiền thật đã thu được bao nhiêu?”

---

## 2. Booking Performance Report

### API
- `POST /api/bookings/revenues/cinemas/search`
- `POST /api/bookings/revenues/cinemas/me/search`
- `POST /api/bookings/revenues/cinemas/export`

### Ý nghĩa
Đây là report hiệu suất vận hành nhìn từ góc độ booking.

Nó trả lời các câu hỏi:
- Có bao nhiêu booking đã tạo ra trong khoảng thời gian này?
- Có bao nhiêu booking đang `PENDING`, `RESERVED`, `CONFIRMED`, `EXPIRED`?
- Tỷ lệ chuyển đổi từ booking sang `CONFIRMED` là bao nhiêu?

### Contract FE cần lưu ý
- Response hiện không còn các field tiền như `ticketSubtotalAmount`, `productSubtotalAmount`, `grossAmount`, `promotionDiscountAmount`, `payableAmount`.
- Response mới có thêm `expiredCount` và `conversionRate`.
- File export Excel có title `BÁO CÁO HIỆU SUẤT BOOKING THEO RẠP`, không còn title kiểu doanh thu.
- Note handoff riêng cho frontend: [booking-service/BOOKING_PERFORMANCE_REPORT_NOTE.md](./booking-service/BOOKING_PERFORMANCE_REPORT_NOTE.md)

### Dữ liệu gốc
- `Booking`
- Snapshot/field dùng để tổng hợp:
  - `cinemaId`
  - `filmId`
  - `bookingStatus`
  - `timeCreated`

### Dùng khi nào
- Khi muốn nhìn “đã bán được bao nhiêu booking”.
- Khi cần thống kê trạng thái booking trước khi xét thanh toán thực tế.
- Khi nghiệp vụ cần đo hiệu quả bán hàng từ booking, không phải doanh thu thực thu.

### Ví dụ thực tế
- Một rạp có nhiều booking nhưng nhiều booking chưa thanh toán. Report này vẫn cho thấy lượng booking phát sinh.
- Manager muốn biết trong tuần này rạp mình tạo ra bao nhiêu booking, bao nhiêu booking đã confirm, bao nhiêu booking bị expire.

### Câu dễ nhớ
- Booking performance = “booking vận hành ra sao?”
- Booking report không phải payment report.
- Nếu FE cần hiển thị %, hãy format `conversionRate` ở UI vì backend trả về số thập phân `0..1`.

---

## 3. Showtime Performance Report

### API
- `POST /api/bookings/reports/showtimes/search`
- `POST /api/bookings/reports/showtimes/me/search`
- `POST /api/bookings/reports/showtimes/export`

### Ý nghĩa
Đây là report hiệu suất suất chiếu.

Nó trả lời các câu hỏi:
- Suất chiếu nào bán tốt?
- Suất chiếu nào có occupancy rate cao?
- Một showtime có bao nhiêu booking, bao nhiêu ghế đã bán, sức chứa là bao nhiêu?

### Dữ liệu gốc
- `Booking` được group theo `showtimeId`
- Kết hợp thêm thông tin từ showtime và layout ghế:
  - `showtimeId`
  - `cinemaId`
  - `filmId`
  - `hallId`
  - `startDateTime`
  - `endDateTime`
  - tổng booking
  - tổng ghế đã bán
  - tổng sức chứa
  - occupancy rate

### Export Excel
- Export showtime performance dùng cùng request với search, nhưng bỏ qua `pageRequest.page` và `pageRequest.size`.
- File export luôn chứa toàn bộ item khớp filter/sort hiện tại, không bị cắt theo page size.

### Dùng khi nào
- Khi muốn biết suất nào bán chạy nhất.
- Khi muốn tối ưu lịch chiếu của một phim.
- Khi cần so sánh hiệu suất giữa các rạp hoặc giữa các suất chiếu khác nhau.

### Ví dụ thực tế
- Cùng một phim, suất 18:00 thường kín ghế hơn suất 10:00. Report này cho thấy rõ occupancy rate của từng showtime.
- Manager cần quyết định có nên tăng thêm suất tối thứ 7 hay không. Nhìn report này sẽ biết suất nào đang quá tải, suất nào đang trống.

### Câu dễ nhớ
- Showtime performance = “mỗi suất chiếu có bán tốt không?”

---

## 4. Tóm tắt nhanh để học thuộc

- `payment revenue report` = tổng tiền thực thu theo rạp.
- `booking performance report` = tổng booking theo trạng thái và tỷ lệ chuyển đổi theo rạp.
- `showtime performance report` = hiệu suất từng suất chiếu, đo bằng booking và occupancy.

## 5. Câu trả lời ngắn khi bị hỏi trên lớp

- Nếu hỏi “report payment dùng để làm gì?”:
  - Báo cáo payment dùng để nhìn tiền thực tế và giao dịch thanh toán.
- Nếu hỏi “report booking khác payment ở đâu?”:
  - Booking nhìn theo đơn đặt vé, payment nhìn theo tiền thực thu.
- Nếu hỏi “showtime performance là gì?”:
  - Là report đo suất chiếu nào bán tốt, dựa trên số booking và tỷ lệ lấp ghế.
