# API Payload Samples

Tài liệu này gom các payload request mẫu để frontend gọi các API report và export report.

Quy ước chung:
- Nếu `selectedIds` không gửi hoặc gửi rỗng, backend export toàn bộ dữ liệu đang khớp filter.
- Nếu `selectedIds` có giá trị, backend chỉ export các dòng có `cinemaId` nằm trong danh sách đó.
- `selectedIds` áp dụng cho các report group theo rạp.

## Payment Revenue Report

`POST /api/payments/revenues/cinemas/search`

### Không lọc theo ngày
```json
{
  "cinemaIds": [
    "d7c5d0d8-6f1c-4f5e-9db1-4a8b3f4cf001"
  ],
  "filmIds": [
    "a4a7d2b8-7a1d-4c17-8c7f-f0d0f1c0a111"
  ],
  "pageRequest": {
    "page": 1,
    "size": 20,
    "keyword": "",
    "sortBy": [],
    "filterBy": []
  }
}
```

### Có lọc theo ngày
```json
{
  "dateRange": {
    "from": "2026-05-01T00:00:00",
    "to": "2026-05-31T23:59:59"
  },
  "cinemaIds": [
    "d7c5d0d8-6f1c-4f5e-9db1-4a8b3f4cf001"
  ],
  "filmIds": [
    "a4a7d2b8-7a1d-4c17-8c7f-f0d0f1c0a111"
  ],
  "pageRequest": {
    "page": 1,
    "size": 20,
    "keyword": "",
    "sortBy": [],
    "filterBy": []
  }
}
```

### Export Excel
`POST /api/payments/revenues/cinemas/export`

#### Export toàn bộ theo filter
```json
{
  "dateRange": {
    "from": "2026-05-01T00:00:00",
    "to": "2026-05-31T23:59:59"
  },
  "cinemaIds": [
    "d7c5d0d8-6f1c-4f5e-9db1-4a8b3f4cf001"
  ],
  "filmIds": [
    "a4a7d2b8-7a1d-4c17-8c7f-f0d0f1c0a111"
  ],
  "pageRequest": {
    "page": 1,
    "size": 20,
    "keyword": "",
    "sortBy": [],
    "filterBy": []
  }
}
```

#### Export đúng các dòng đã chọn
```json
{
  "dateRange": {
    "from": "2026-05-01T00:00:00",
    "to": "2026-05-31T23:59:59"
  },
  "cinemaIds": [
    "d7c5d0d8-6f1c-4f5e-9db1-4a8b3f4cf001"
  ],
  "filmIds": [
    "a4a7d2b8-7a1d-4c17-8c7f-f0d0f1c0a111"
  ],
  "selectedIds": [
    "d7c5d0d8-6f1c-4f5e-9db1-4a8b3f4cf001",
    "f4a7d2b8-7a1d-4c17-8c7f-f0d0f1c0a222"
  ],
  "pageRequest": {
    "page": 1,
    "size": 20,
    "keyword": "",
    "sortBy": [],
    "filterBy": []
  }
}
```

## Booking Revenue Report

`POST /api/bookings/revenues/cinemas/search`

### Không lọc theo ngày
```json
{
  "cinemaIds": [
    "d7c5d0d8-6f1c-4f5e-9db1-4a8b3f4cf001"
  ],
  "filmIds": [
    "a4a7d2b8-7a1d-4c17-8c7f-f0d0f1c0a111"
  ],
  "pageRequest": {
    "page": 1,
    "size": 20,
    "keyword": "",
    "sortBy": [],
    "filterBy": []
  }
}
```

### Có lọc theo ngày
```json
{
  "dateRange": {
    "from": "2026-05-01T00:00:00",
    "to": "2026-05-31T23:59:59"
  },
  "cinemaIds": [
    "d7c5d0d8-6f1c-4f5e-9db1-4a8b3f4cf001"
  ],
  "filmIds": [
    "a4a7d2b8-7a1d-4c17-8c7f-f0d0f1c0a111"
  ],
  "pageRequest": {
    "page": 1,
    "size": 20,
    "keyword": "",
    "sortBy": [],
    "filterBy": []
  }
}
```

### Export Excel
`POST /api/bookings/revenues/cinemas/export`

#### Export toàn bộ theo filter
```json
{
  "dateRange": {
    "from": "2026-05-01T00:00:00",
    "to": "2026-05-31T23:59:59"
  },
  "cinemaIds": [
    "d7c5d0d8-6f1c-4f5e-9db1-4a8b3f4cf001"
  ],
  "filmIds": [
    "a4a7d2b8-7a1d-4c17-8c7f-f0d0f1c0a111"
  ],
  "pageRequest": {
    "page": 1,
    "size": 20,
    "keyword": "",
    "sortBy": [],
    "filterBy": []
  }
}
```

#### Export đúng các dòng đã chọn
```json
{
  "dateRange": {
    "from": "2026-05-01T00:00:00",
    "to": "2026-05-31T23:59:59"
  },
  "cinemaIds": [
    "d7c5d0d8-6f1c-4f5e-9db1-4a8b3f4cf001"
  ],
  "filmIds": [
    "a4a7d2b8-7a1d-4c17-8c7f-f0d0f1c0a111"
  ],
  "selectedIds": [
    "d7c5d0d8-6f1c-4f5e-9db1-4a8b3f4cf001",
    "f4a7d2b8-7a1d-4c17-8c7f-f0d0f1c0a222"
  ],
  "pageRequest": {
    "page": 1,
    "size": 20,
    "keyword": "",
    "sortBy": [],
    "filterBy": []
  }
}
```

## Showtime Performance Report

`POST /api/bookings/reports/showtimes/search`

### Không lọc theo ngày
```json
{
  "cinemaIds": [
    "d7c5d0d8-6f1c-4f5e-9db1-4a8b3f4cf001"
  ],
  "filmIds": [
    "a4a7d2b8-7a1d-4c17-8c7f-f0d0f1c0a111"
  ],
  "pageRequest": {
    "page": 1,
    "size": 20,
    "keyword": "",
    "sortBy": [],
    "filterBy": []
  }
}
```

### Có lọc theo ngày
```json
{
  "dateRange": {
    "from": "2026-05-01T00:00:00",
    "to": "2026-05-31T23:59:59"
  },
  "cinemaIds": [
    "d7c5d0d8-6f1c-4f5e-9db1-4a8b3f4cf001"
  ],
  "filmIds": [
    "a4a7d2b8-7a1d-4c17-8c7f-f0d0f1c0a111"
  ],
  "pageRequest": {
    "page": 1,
    "size": 20,
    "keyword": "",
    "sortBy": [],
    "filterBy": []
  }
}
```

## Showtime Search

`POST /api/showtimes/search`

### Request sample
```json
{
  "page": 1,
  "size": 20,
  "keyword": "",
  "sortBy": [
    {
      "field": "START_DATE_TIME",
      "direction": "ASC"
    }
  ],
  "filterBy": [
    {
      "field": "FILM_ID",
      "operator": "EQ",
      "value": "a4a7d2b8-7a1d-4c17-8c7f-f0d0f1c0a111"
    },
    {
      "field": "START_DATE_TIME",
      "operator": "BETWEEN",
      "value": [
        "2026-05-29T00:00:00",
        "2026-05-29T23:59:59"
      ]
    }
  ]
}
```

### Response note
- `ShowTimeResponse` includes `totalSeatCapacity`, `occupiedSeats`, and `availableSeats`.
- FE can disable a showtime when `availableSeats = 0`.

## Ghi chú

- `dateRange` là payload riêng, chỉ gửi khi muốn lọc theo thời gian.
- `pageRequest` luôn giữ phần phân trang, keyword, sort và filter.
- `cinemaIds` và `filmIds` là mảng optional, có thể bỏ trống nếu muốn xem toàn scope.
- `selectedIds` chỉ dùng cho export Excel, không bắt buộc ở report JSON.

## Film Search

`POST /api/films/search`

```json
{
  "cursor": null,
  "size": 20,
  "keyword": "",
  "sortBy": [],
  "filterBy": [],
  "dateRange": {
    "from": "2026-01-01T00:00:00",
    "to": "2026-05-31T23:59:59"
  }
}
```

## Film Customer Search

`POST /api/films/customer/search`

```json
{
  "cursor": null,
  "size": 20,
  "keyword": "",
  "sortBy": [],
  "filterBy": [],
  "dateRange": {
    "from": "2026-01-01T00:00:00",
    "to": "2026-05-31T23:59:59"
  }
}
```
