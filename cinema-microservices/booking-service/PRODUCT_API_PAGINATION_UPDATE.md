# Cập nhật phân trang API Product (booking-service)

## Mục tiêu

- Chuyển 2 API product từ trả toàn bộ `List<ProductResponse>` sang phân trang chuẩn `PageRequest` / `PageResponse`.

## API đã thay đổi

### 1) Products theo operator cinema
- Cũ: `GET /api/bookings/products/me`
- Mới: `POST /api/bookings/products/me/search`

Request body:
```json
{
  "page": 1,
  "size": 20
}
```

Response data:
```json
{
  "data": [
    {
      "id": "uuid",
      "cinemaId": "uuid",
      "name": "Combo 1",
      "type": "COMBO",
      "price": 99000,
      "description": "....",
      "imageUrl": "....",
      "status": "ACTIVE",
      "timeCreated": "2026-05-13T23:50:00",
      "timeUpdated": "2026-05-13T23:50:00"
    }
  ],
  "currentPage": 1,
  "totalPages": 5,
  "totalElements": 95,
  "size": 20,
  "hasNext": true,
  "hasPrevious": false
}
```

### 2) Products theo cinemaId
- Cũ: `GET /api/bookings/products/cinemas/{cinemaId}`
- Mới: `POST /api/bookings/products/cinemas/{cinemaId}/search`

Request body:
```json
{
  "page": 1,
  "size": 20
}
```

Response: cùng format `PageResponse<ProductResponse>` như trên.

## Logic phân trang

- Dùng `PageRequest<ProductField>` từ `common-lib`.
- Truy vấn JPA `Page<Product>` theo `cinemaId` + `isDeleted = false`.
- Sort mặc định: `timeCreated DESC` (giữ hành vi sắp xếp cũ).

## Các file đã sửa

- `booking-service/src/main/java/com/cinema/booking_service/controller/ProductController.java`
- `booking-service/src/main/java/com/cinema/booking_service/services/ProductService.java`
- `booking-service/src/main/java/com/cinema/booking_service/services/impl/ProductServiceImpl.java`
- `booking-service/src/main/java/com/cinema/booking_service/repository/ProductRepository.java`
- `booking-service/src/main/java/com/cinema/booking_service/dto/request/ProductField.java` (file mới)

## Ghi chú tương thích

- Đây là thay đổi phá vỡ tương thích ở route/method cho 2 API list product.
- Frontend/API gateway cần đổi từ `GET` sang `POST` và gửi body phân trang.

## Kiểm tra build

- Đã chạy compile module `booking-service` thành công:
- Lệnh: `booking-service\\mvnw.cmd -DskipTests compile`

