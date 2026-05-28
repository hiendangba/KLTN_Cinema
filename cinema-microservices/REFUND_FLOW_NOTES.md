# Ghi Chú Chi Tiết Luồng Refund

## 1. Mục đích tài liệu
- Ghi lại đầy đủ các vấn đề, điểm mơ hồ và mâu thuẫn hiện tại của luồng `refund`.
- Chỉ phản ánh hiện trạng đang có trong code và tài liệu.
- Không đưa ra phương án thiết kế, không chốt rule mới, không xem đây là tài liệu implementation.

## 2. Phạm vi rà soát
- `payment-service`:
  - API refund tại `POST /api/payments/sessions/{bookingId}/refund`.
  - Trạng thái `REFUND_PENDING`, `REFUNDED` trong `PaymentTransactionStatus`.
  - Luồng webhook hiện tại (`ORDER_PAID`, `TRANSACTION_VOID`).
  - Trường dữ liệu refund trên `payment_transaction`.
- `booking-service`:
  - Luồng hủy booking của customer.
  - Luồng cập nhật trạng thái booking của operator/admin.
  - Trạng thái payment trong booking (`UNPAID`, `PAID`, `FAILED`, `REFUNDED`).
- Tài liệu hiện tại:
  - `README.md`.
  - `TECHNICAL_AGENT_GUIDE.md`.

## 3. Hiện trạng thực tế của refund trong code
- Refund đã có endpoint và dữ liệu nội bộ trong `payment-service`.
- Refund chưa có luồng tích hợp hoàn tất với cổng thanh toán thật.
- Refund hiện dừng ở trạng thái nội bộ:
  - Có thể set `REFUND_PENDING`.
  - Chưa có đường xác nhận chuyển sang `REFUNDED` từ sự kiện provider.
- Luồng webhook hiện có đang ưu tiên thanh toán thành công/thất bại:
  - Xử lý `ORDER_PAID`.
  - Xử lý `TRANSACTION_VOID`.
  - Chưa thể hiện rõ sự kiện `refund completed` từ provider.

## 4. Các trạng thái liên quan và điểm chưa chốt

### 4.1. Trạng thái booking
- `PENDING`
- `RESERVED`
- `CONFIRMED`
- `CANCELLED`
- `EXPIRED`

### 4.2. Trạng thái payment ở booking
- `UNPAID`
- `PAID`
- `FAILED`
- `REFUNDED`

### 4.3. Trạng thái payment transaction
- `PENDING`
- `PAID`
- `FAILED`
- `EXPIRED`
- `REFUND_PENDING`
- `REFUNDED`

### 4.4. Điểm chưa chốt giữa các state machine
- Chưa có mapping chính thức giữa:
  - `Booking.paymentStatus = REFUNDED`
  - và `PaymentTransaction.status = REFUNDED`.
- Chưa rõ khi transaction refund thành công thì:
  - booking status giữ `CONFIRMED`, hay chuyển `CANCELLED`, hay một terminal state khác.
- Chưa rõ khi refund thất bại thì:
  - có rollback trạng thái nội bộ không,
  - hay giữ nguyên `REFUND_PENDING`,
  - hay chuyển trạng thái lỗi riêng cho refund.

## 5. Mâu thuẫn nghiệp vụ hiện tại giữa booking và refund
- Customer không được hủy booking đã `CONFIRMED + PAID`.
- Refund nội bộ lại ngầm giả định có tình huống cần hoàn tiền sau khi đã thanh toán.
- Luồng update status của operator/admin có thể đổi trạng thái booking, nhưng chưa có ràng buộc rõ với refund.
- Vì vậy đang tồn tại khoảng trống:
  - refund tồn tại ở payment layer,
  - nhưng event nghiệp vụ để hợp thức hóa refund ở booking layer chưa thống nhất.

## 6. Trigger refund chưa rõ ràng
- Chưa có quyết định chính thức refund được phép phát sinh từ sự kiện nào.
- Các trigger khả dĩ đang tồn tại ở mức giả định (chưa chốt):
  - thao tác user,
  - thao tác admin/operator,
  - thao tác hệ thống,
  - thao tác tài chính/đối soát.
- Chưa có tài liệu rule khẳng định:
  - trigger nào hợp lệ,
  - trigger nào không hợp lệ.

## 7. Phân quyền refund chưa chốt
- Chưa có rule chính thức actor nào được khởi tạo refund.
- Chưa có rule chính thức actor nào được duyệt/refuse refund.
- Chưa rõ phạm vi role của các nhóm:
  - customer,
  - admin,
  - manager/staff,
  - finance/backoffice.
- Endpoint refund hiện có ownership check theo booking user, nhưng đó chưa phải định nghĩa nghiệp vụ cuối cùng.

## 8. Khoảng trống dữ liệu và audit
- Chưa có định nghĩa nguồn sự thật cuối cho trạng thái refund:
  - nội bộ DB,
  - hay callback từ provider.
- Chưa có quy ước hoàn chỉnh về audit trail refund:
  - ai tạo,
  - ai duyệt,
  - lý do nghiệp vụ,
  - mốc thời gian từng bước.
- Chưa có mã lỗi/rule riêng cho các tình huống refund đặc thù:
  - quá hạn refund,
  - refund trùng,
  - refund vượt số tiền hợp lệ theo nghiệp vụ thực tế.

## 9. Khoảng trống tích hợp với cổng thanh toán
- Chưa có contract refund chính thức với provider trong code hiện tại.
- Chưa có callback/event refund completion được xử lý end-to-end.
- Chưa có state sync hoàn chỉnh giữa:
  - payment transaction nội bộ,
  - booking payment status,
  - trạng thái từ provider.
- Reconciliation có thống kê refund, nhưng thiếu nguồn xác nhận provider cho trường hợp hoàn tất thật.

## 10. Khoảng trống tài liệu hiện tại
- Tài liệu đã ghi rõ refund chưa phải phần core hoàn chỉnh.
- Tuy nhiên vẫn có nguy cơ hiểu nhầm vì:
  - endpoint refund đã tồn tại,
  - trạng thái refund đã tồn tại,
  - reconciliation đã có chỉ số refund.
- Chưa có một tài liệu nghiệp vụ chính thức định nghĩa:
  - phạm vi áp dụng refund,
  - điều kiện áp dụng,
  - actor áp dụng,
  - luồng kết thúc.

## 11. Kết luận hiện trạng
- Refund hiện có ở mức “khả năng kỹ thuật nội bộ”, chưa phải “luồng nghiệp vụ vận hành đầy đủ”.
- Điểm thiếu lớn nhất không phải API, mà là:
  - trigger nghiệp vụ,
  - state transition giữa booking/payment/provider,
  - và quyền/phê duyệt.

## 12. Ghi chú bắt buộc
- File này chỉ ghi vấn đề và điểm chưa rõ.
- File này không đề xuất phương án xử lý.
- File này không thay thế quyết định nghiệp vụ chính thức.

