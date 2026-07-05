# Booking Performance Report Note for Frontend

Tài liệu này ghi lại đúng contract hiện tại của booking report sau khi đổi từ logic "revenue" sang "performance".

## API không đổi

- `POST /api/bookings/revenues/cinemas/search`
- `POST /api/bookings/revenues/cinemas/me/search`
- `POST /api/bookings/revenues/cinemas/export`
- `POST /api/bookings/reports/showtimes/search`
- `POST /api/bookings/reports/showtimes/me/search`
- `POST /api/bookings/reports/showtimes/export`

## Request không đổi

Frontend vẫn gửi các field sau trong body:

- `dateRange`
- `cinemaIds`
- `filmIds`
- `selectedIds`
- `pageRequest`

## Export khong bi phan trang

- Excel export lay toan bo item khop filter/sort hien tai.
- `pageRequest.page` va `pageRequest.size` chi dung cho JSON search, khong cat du lieu trong file export.

## Response đã đổi

Frontend cần cập nhật mapping cho report booking vì payload hiện tại không còn là report doanh thu tiền nữa.

### Bỏ các field tiền

- `ticketSubtotalAmount`
- `productSubtotalAmount`
- `grossAmount`
- `promotionDiscountAmount`
- `payableAmount`

### Thêm các field mới

- `expiredCount`
- `conversionRate`

### Ý nghĩa `conversionRate`

- Đây là tỷ lệ chuyển đổi từ booking sang `CONFIRMED`.
- Giá trị trả về là số thập phân từ `0` đến `1`.
- Nếu muốn hiển thị phần trăm, frontend tự format ở UI.

## Export Excel đã đổi

- Tên file export vẫn do backend trả về, nhưng nội dung sheet đã đổi sang báo cáo hiệu suất booking.
- Title trên sheet: `BÁO CÁO HIỆU SUẤT BOOKING THEO RẠP`
- Header tiếng Việt hiện tại:
  - `Mã rạp`
  - `Tên rạp`
  - `Tổng booking`
  - `Số booking chờ xử lý`
  - `Số booking giữ chỗ`
  - `Số booking đã xác nhận`
  - `Số booking hết hạn`
  - `Tỷ lệ chuyển đổi`

## FE cần chỉnh gì

- Không map nữa các cột tiền ở booking report.
- Hiển thị thêm `expiredCount`.
- Format `conversionRate` thành phần trăm nếu muốn giống báo cáo business.
- Nếu UI đang đặt tên là "booking revenue", nên đổi text sang "booking performance" để khớp nghiệp vụ mới.
