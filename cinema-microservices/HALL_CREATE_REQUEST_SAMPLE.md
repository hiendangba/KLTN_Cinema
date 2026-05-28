# Mẫu API tạo hall cho frontend

## Endpoint
- `POST /api/halls`

## Header
- `Authorization: Bearer <access_token>`
- `Content-Type: application/json`

## JSON mẫu
```json
{
  "name": "Rạp 01 - Tầng 2",
  "status": "ACTIVE",
  "layoutDefinition": {
    "totalRows": 5,
    "totalCols": 6,
    "screenPosition": "TOP",
    "cells": [
      { "row": 1, "col": 1, "type": "SEAT", "seatType": "VIP" },
      { "row": 1, "col": 2, "type": "SEAT", "seatType": "VIP" },
      { "row": 1, "col": 3, "type": "AISLE" },
      { "row": 1, "col": 4, "type": "SEAT", "seatType": "VIP" },
      { "row": 1, "col": 5, "type": "SEAT", "seatType": "VIP" },
      { "row": 1, "col": 6, "type": "BLOCKED" }
    ]
  },
  "imagePaths": [
    "/uploads/halls/rap-01-main.jpg",
    "/uploads/halls/rap-01-view-2.jpg"
  ]
}
```

## Lưu ý cho FE
- `status`: `ACTIVE` hoặc `MAINTENANCE`.
- `screenPosition`: `TOP | BOTTOM | LEFT | RIGHT`.
- `type`: `SEAT | AISLE | BLOCKED`.
- Nếu `type = SEAT` thì cần `seatType` (`STANDARD | VIP | COUPLE`).
- Nếu `type = AISLE` hoặc `BLOCKED` thì không gửi `seatType`.
