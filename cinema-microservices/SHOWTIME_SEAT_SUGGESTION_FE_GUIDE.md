# FE Guide: Seat Suggestion API

Tài liệu này dành cho FE để dùng đúng tính năng gợi ý ghế của `showtime-service`.

Mục tiêu của API là trả về **danh sách gợi ý tốt nhất theo thứ tự ưu tiên**, không phải lúc nào cũng đủ 5 phương án.

## 1. API

`POST /api/showtimes/{showtimeId}/seat-suggestions`

### Path parameter
- `showtimeId`: suất chiếu cần gợi ý ghế

### Request body
```json
{
  "seatCount": 3,
  "preferCoupleSeat": true
}
```

### Rule input
- `seatCount` chỉ được từ `1` đến `5`
- `preferCoupleSeat` là boolean bắt buộc
- FE chỉ cần gửi đúng 2 field này

## 2. Response

API trả `APIResponse<SeatSuggestionResponse>` theo style chung của repo.

### Shape mong đợi
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Seat suggestions fetched successfully",
  "data": {
    "showtimeId": "7b9b...",
    "requestedSeatCount": 3,
    "preferCoupleSeat": true,
    "candidates": [
      {
        "seatCodes": ["J7", "J8", "I8"],
        "seatCount": 3,
        "hasCoupleSeats": true,
        "totalPrice": 290000,
        "score": 128500,
        "reason": "Ưu tiên ghế couple theo cặp cố định, ghép thêm ghế thường gần cặp ghế"
      }
    ]
  }
}
```

### Điểm quan trọng
- `candidates` là **tối đa 5 phần tử**
- nếu backend chỉ tìm ra 2 hoặc 3 phương án thì FE chỉ nhận đúng số đó
- thứ tự backend trả về chính là thứ tự ưu tiên

## 3. Luật gợi ý ghế

### Ưu tiên chính
1. Ghế phải hợp lệ và còn trống
2. Nếu `preferCoupleSeat = true` thì ưu tiên **ghế couple theo cặp cố định**
3. Ưu tiên phương án gần trung tâm
4. Nếu cần fallback thì vẫn ưu tiên phương án ít bị tách cụm hơn
5. Nếu hòa điểm thì ưu tiên hàng xa màn hình hơn theo rule hiện tại của backend

### Luật couple
- Ghế `COUPLE` là **1 ghế đôi trên UI**, nhưng backend vẫn lưu và trả về **2 seat code riêng**
- Mỗi ghế couple có **ranh giới cặp cố định** từ layout/backend, ví dụ:
  - `J5-J6`
  - `J7-J8`
  - `J9-J10`
- Backend chỉ được chọn theo đúng cặp cố định đó
- Backend **không được cắt lẻ** một nửa cặp couple
- Backend **không được trượt cửa sổ ghế liền nhau** để tạo cặp mới, ví dụ:
  - không được coi `J6-J7` là một cặp hợp lệ
  - không được trả `J5, J6, J7`
  - không được trả `J6, J7, J8, J9`
- Nếu một ghế trong cặp không available thì loại cả cặp, ví dụ:
  - `J8` bị book thì `J7-J8` không còn là couple hợp lệ

### Luật fallback
- Nếu không có đủ cặp couple phù hợp, backend sẽ fallback sang tổ hợp mixed:
  - `1 couple pair + 1 single`
  - `1 couple pair + 2 single`
  - hoặc toàn ghế thường nếu không có cặp couple nào hợp lệ
- Nhưng ngay cả khi fallback, backend vẫn **không chọn lẻ ghế couple**

### Khi nào báo lỗi
- Nếu tổng ghế trống không đủ cho `seatCount`, backend trả lỗi “không đủ ghế”

## 4. FE cần hiểu thế nào

### Input UI
- ô nhập số ghế: `1..5`
- checkbox: `Ưu tiên ghế couple`

### Gợi ý UI
- FE **không nên** chặn số lẻ chỉ vì bật couple
- FE chỉ cần gửi:
  - `seatCount`
  - `preferCoupleSeat`

### Render kết quả
- render danh sách theo đúng thứ tự backend trả về
- candidate đầu tiên là gợi ý mạnh nhất
- nếu `hasCoupleSeats = true` thì có thể highlight rõ đây là phương án có couple
- khi gặp cặp couple, FE có thể render `J7-J8` thành **1 ghế đôi**
- FE không nên hiểu `seatCodes.length` là số ô ghế hiển thị khi candidate có couple

## 5. Ví dụ ngắn

### 3 ghế + ưu tiên couple
- backend nên ưu tiên kiểu:
  - `1 cặp couple cố định + 1 ghế thường gần cặp ghế`

### 4 ghế + ưu tiên couple
- backend nên ưu tiên kiểu:
  - `2 cặp couple cố định`
- không được chọn kiểu cắt lẻ như lấy `J6, J7, J8, J9`

## 6. Kết luận cho FE

- `top 5` là giới hạn tối đa, không phải số bắt buộc
- `seatCount` luôn từ `1` đến `5`
- `preferCoupleSeat = true` vẫn có thể dùng với số lẻ
- backend ưu tiên couple **theo cặp cố định**, không cắt lẻ
- nếu không đủ cặp couple, backend sẽ dùng tối đa số cặp có thể rồi ghép ghế thường
- response vẫn giữ `seatCodes` như hiện tại để không làm vỡ contract
