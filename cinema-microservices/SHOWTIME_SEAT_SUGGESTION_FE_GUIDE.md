# FE Guide: Seat Suggestion API

Tài liệu này dành cho FE để dùng đúng tính năng gợi ý ghế của `showtime-service`.

Mục tiêu của API này là trả về **danh sách gợi ý tốt nhất theo thứ tự ưu tiên**, không phải lúc nào cũng đủ 5 phần tử.

## 1. API

`POST /api/showtimes/{showtimeId}/seat-suggestions`

### Path parameter
- `showtimeId`: suất chiếu cần gợi ý ghế

### Request body
```json
{
  "seatCount": 2,
  "preferCoupleSeat": true
}
```

### Rule input
- `seatCount` chỉ được từ `1` đến `5`
- nếu `preferCoupleSeat = true` thì `seatCount` **phải là số chẵn**
- chỉ cần đúng 2 field này, không cần thêm field khác

## 2. Response

API trả `APIResponse<SeatSuggestionResponse>` theo style của repo.

### Shape mong đợi
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Seat suggestions fetched successfully",
  "path": "/api/showtimes/7b9b.../seat-suggestions",
  "timestamp": "2026-06-09T12:00:00",
  "data": {
    "showtimeId": "7b9b...",
    "requestedSeatCount": 4,
    "preferCoupleSeat": true,
    "candidates": [
      {
        "seatCodes": ["A5", "A6", "A7", "A8"],
        "seatCount": 4,
        "hasCoupleSeats": true,
        "totalPrice": 320000,
        "score": 98,
        "reason": "Gần trung tâm, cụm ghế liền nhau, có couple"
      },
      {
        "seatCodes": ["B5", "B6", "B7", "B8"],
        "seatCount": 4,
        "hasCoupleSeats": true,
        "totalPrice": 320000,
        "score": 94,
        "reason": "Cụm ghế đẹp, xa màn hình hơn"
      }
    ]
  }
}
```

### Điểm quan trọng
- `candidates` là **up to 5 phần tử**
- nếu backend chỉ tìm ra 3 phương án thì FE chỉ nhận 3
- không có chuyện backend ép luôn trả đủ 5

## 3. Luật gợi ý ghế

### Ưu tiên chính
1. Ghế phải **liền kề thật sự**
2. Ưu tiên **gần trung tâm** nhất
3. Nếu `preferCoupleSeat = true` thì **ưu tiên block couple**
4. Nếu cùng điểm thì ưu tiên **hàng xa màn hình hơn**

### Luật couple
- `preferCoupleSeat = true` chỉ hợp lệ khi `seatCount` là số chẵn
- nếu tick couple thì backend ưu tiên block couple, nhưng vẫn phải thỏa rule ghế liền nhau

### Luật fallback
- nếu không có block đúng số ghế, backend có thể fallback sang tổ hợp nhỏ hơn
- ví dụ nhập `4` nhưng không có block 4 ghế liền nhau thì backend có thể trả phương án kiểu `3 + 1`, `2 + 2`, ...
- phương án fallback vẫn phải được xếp theo độ gần trung tâm

### Khi nào báo lỗi
- nếu tổng ghế trống **không đủ** cho `seatCount` thì backend trả lỗi “không đủ ghế”

## 4. FE cần hiểu thế nào

### Input UI
- ô nhập số ghế: `1..5`
- checkbox: `Ưu tiên ghế couple`

### Gợi ý UI
- nếu checkbox couple được bật, FE nên chặn hoặc disable số lẻ (`1, 3, 5`)
- FE chỉ cần gửi 2 field:
  - `seatCount`
  - `preferCoupleSeat`

### Render kết quả
- FE render danh sách theo đúng thứ tự backend trả về
- phần tử đầu tiên là gợi ý mạnh nhất
- nếu có ít hơn 5 phần tử thì vẫn là dữ liệu hợp lệ

## 5. Ví dụ request/response ngắn

### Request
```json
{
  "seatCount": 2,
  "preferCoupleSeat": true
}
```

### Response khi có 3 candidate
```json
{
  "data": {
    "requestedSeatCount": 2,
    "preferCoupleSeat": true,
    "candidates": [
      { "seatCodes": ["A5", "A6"], "seatCount": 2 },
      { "seatCodes": ["B5", "B6"], "seatCount": 2 },
      { "seatCodes": ["C5", "C6"], "seatCount": 2 }
    ]
  }
}
```

### Response khi không đủ ghế
```json
{
  "success": false,
  "code": "...",
  "message": "Không đủ ghế trống để gợi ý"
}
```

## 6. Kết luận cho FE

- `top 5` là **giới hạn tối đa**, không phải số bắt buộc
- `seatCount` luôn phải từ `1` đến `5`
- `preferCoupleSeat = true` thì `seatCount` phải chẵn
- backend ưu tiên ghế liền nhau, gần trung tâm, rồi mới tới couple và tie-break hàng xa màn hình hơn
- nếu không đủ ghế trống thì backend báo lỗi, FE không tự đoán
