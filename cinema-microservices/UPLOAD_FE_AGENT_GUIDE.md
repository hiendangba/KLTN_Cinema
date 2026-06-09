# Upload FE Agent Guide

Tài liệu này mô tả cách frontend tích hợp với `upload-service` của CinemaStar.

Mục tiêu:
- FE agent có thể triển khai upload ảnh và video mà không cần đọc code backend.
- Làm rõ 2 flow khác nhau:
  - upload ảnh theo batch
  - upload video lớn theo chunked session
- Chỉ dùng đúng contract backend hiện tại, không tự suy diễn thêm.

---

## 1. Tổng quan

Backend có **2 flow riêng**:

### Upload ảnh
- Endpoint: `POST /api/uploads/images`
- Dùng khi upload:
  - avatar
  - poster
  - gallery ảnh
  - ảnh nhỏ/trung bình
- Request:
  - `multipart/form-data`
  - field tên đúng là `files`
- Giới hạn:
  - tối đa `5` file mỗi request
- Response:
  - `APIResponse.data.files`

### Upload video lớn
- Dùng cho video lớn, kể cả video rất dài
- Không upload trực tiếp bằng 1 request `multipart/form-data`
- Flow:
  1. tạo upload session
  2. cắt file thành chunk
  3. upload từng chunk
  4. hỏi status nếu cần resume
  5. gọi complete

---

## 2. Assumptions cho FE

- Tất cả request `/api/uploads/**` là **public**, không bắt buộc token
- Tất cả file sau khi upload xong sẽ là **public**
- Public URL trả về có thể dùng ngay để:
  - preview
  - submit tiếp vào form business
  - render media
- Không tự generate URL ở FE, luôn dùng URL backend trả về
- Nếu request đi qua một lớp upstream đáng tin cậy có gắn `X-User-ID`, backend sẽ lưu lại người đẩy; nếu không có thì owner sẽ để `null`

---

## 3. Envelope response chuẩn

Tất cả response thành công theo dạng:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "string",
  "path": "/api/...",
  "timestamp": "2026-06-09T12:00:00",
  "data": {}
}
```

Response lỗi thường theo dạng:

```json
{
  "success": false,
  "code": "9204",
  "message": "Kích thước tệp vượt quá giới hạn cho phép",
  "path": "/api/...",
  "timestamp": "2026-06-09T12:00:00",
  "data": null
}
```

FE nên đọc tối thiểu:
- `success`
- `code`
- `message`
- `data`

---

## 4. Flow ảnh

### Endpoint
- `POST /api/uploads/images`

### Request

Content type:

```http
multipart/form-data
```

Field bắt buộc:
- `files`

### Quan trọng
- Không gửi field tên `files[]` nếu client lib không map đúng.
- Backend hiện bind theo tên **`files`**.

### Ví dụ với `FormData`

```ts
const formData = new FormData();

for (const file of selectedFiles) {
  formData.append("files", file);
}

const response = await fetch("/api/uploads/images", {
  method: "POST",
  body: formData
});
```

### Response thành công

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Upload files successfully",
  "path": "/api/uploads/images",
  "timestamp": "2026-06-09T12:00:00",
  "data": {
    "files": [
      {
        "fileId": "uuid",
        "originalFileName": "poster.jpg",
        "contentType": "image/jpeg",
        "mediaType": "IMAGE",
        "size": 1843221,
        "url": "https://your-domain/media/2026/06/uuid.jpg"
      }
    ]
  }
}
```

### FE cần làm sau khi upload ảnh thành công

- đọc `response.data.files`
- render preview từ `url`
- lưu `fileId` nếu business API sau đó cần reference
- nếu chỉ cần hiển thị ngay thì `url` là đủ

### Rule xử lý lỗi

- nếu chọn hơn 5 file: chặn ở FE trước khi gọi API
- nếu API vẫn trả lỗi:
  - hiển thị `message`
  - không giữ state “upload success” cũ
- batch ảnh là **atomic**
  - 1 file lỗi => fail cả request

---

## 5. Flow video lớn

Flow này dùng cho video không nên upload thẳng bằng 1 request duy nhất.

### Bước 1: tạo session

#### Endpoint
- `POST /api/uploads/videos/sessions`

#### Request body

```json
{
  "originalFileName": "movie.mp4",
  "contentType": "video/mp4",
  "size": 482211230
}
```

#### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Video upload session created",
  "data": {
    "sessionId": "session-uuid",
    "chunkSize": 20971520,
    "expiresAt": "2026-06-09T13:00:00"
  }
}
```

#### FE cần lưu lại
- `sessionId`
- `chunkSize`
- `expiresAt`

---

### Bước 2: cắt file thành chunk

FE phải cắt `File` theo `chunkSize` backend trả về.

```ts
function splitFileIntoChunks(file: File, chunkSize: number) {
  const chunks: Blob[] = [];

  let start = 0;
  while (start < file.size) {
    const end = Math.min(start + chunkSize, file.size);
    chunks.push(file.slice(start, end));
    start = end;
  }

  return chunks;
}
```

---

### Bước 3: upload từng chunk

#### Endpoint
- `PUT /api/uploads/videos/sessions/{sessionId}/chunks/{index}`

#### Body
- raw bytes của chunk

#### Content type

```http
application/octet-stream
```

#### Ví dụ với `fetch`

```ts
async function uploadChunk(sessionId: string, index: number, chunk: Blob) {
  const response = await fetch(`/api/uploads/videos/sessions/${sessionId}/chunks/${index}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/octet-stream"
    },
    body: chunk
  });

  return response.json();
}
```

#### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Video chunk uploaded successfully",
  "data": {
    "sessionId": "session-uuid",
    "chunkIndex": 0,
    "chunkSize": 20971520,
    "uploadedChunks": 1,
    "totalChunks": 48,
    "progressPercent": 2.08
  }
}
```

#### FE cần làm

- cập nhật progress bar theo `progressPercent`
- đánh dấu chunk nào đã xong
- nếu lỗi mạng ở chunk nào thì chỉ retry chunk đó

---

### Bước 4: resume khi upload bị đứt

#### Endpoint
- `GET /api/uploads/videos/sessions/{sessionId}`

#### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Video upload session status fetched successfully",
  "data": {
    "sessionId": "session-uuid",
    "status": "UPLOADING",
    "uploadedChunks": 12,
    "totalChunks": 48,
    "receivedChunks": [0,1,2,3,4,5,6,7,8,9,10,11],
    "expiresAt": "2026-06-09T13:00:00"
  }
}
```

#### FE resume logic

- gọi status API
- backend trả `receivedChunks`
- FE chỉ upload những chunk còn thiếu

Pseudo logic:

```ts
const uploaded = new Set(status.data.receivedChunks);

for (let i = 0; i < chunks.length; i++) {
  if (uploaded.has(i)) continue;
  await uploadChunk(sessionId, i, chunks[i]);
}
```

---

### Bước 5: complete session

#### Endpoint
- `POST /api/uploads/videos/sessions/{sessionId}/complete`

#### Body
- không cần body

#### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Video uploaded successfully",
  "data": {
    "file": {
      "fileId": "uuid",
      "originalFileName": "movie.mp4",
      "contentType": "video/mp4",
      "mediaType": "VIDEO",
      "size": 482211230,
      "url": "https://your-domain/media/2026/06/uuid.mp4"
    }
  }
}
```

#### FE cần làm

- lấy `data.file.url` để preview hoặc submit tiếp
- lưu `data.file.fileId` nếu business API cần reference

---

## 6. FE UI/UX khuyến nghị

### Với ảnh
- cho phép chọn nhiều ảnh
- chặn chọn quá 5 file ở client
- preview local trước hoặc preview sau upload đều được
- khi upload xong dùng URL backend trả về làm source chính

### Với video
- hiển thị progress upload theo chunk
- lưu `sessionId` trong state nếu user chưa rời trang
- nếu upload fail giữa chừng:
  - cho nút `Resume`
  - không bắt user upload lại từ đầu ngay

### Mixed media
Nếu UI cho user chọn cả ảnh và video trong cùng màn hình:
- ảnh đi flow `/api/uploads/images`
- video đi flow `videos/sessions`
- FE không nên cố nhét ảnh và video vào cùng một request

---

## 7. Error handling FE cần hiểu

### Các lỗi quan trọng

- `9201` — quá số lượng file cho ảnh
- `9202` — file rỗng
- `9203` — content type không hỗ trợ
- `9204` — file quá lớn
- `9205` — tổng request quá lớn
- `9206` — không tìm thấy session video
- `9207` — session video hết hạn
- `9208` — session ở trạng thái không hợp lệ
- `9209` — chunk index sai
- `9210` — complete khi chưa đủ chunk
- `9211` — upload thất bại nội bộ

### FE nên xử lý tối thiểu

- luôn show `message` từ backend
- với lỗi session hết hạn:
  - xóa `sessionId`
  - tạo session mới
- với lỗi chunk fail:
  - retry chunk hiện tại
- với lỗi complete thiếu chunk:
  - gọi lại status
  - upload nốt chunk còn thiếu

---

## 8. Pseudo flow đầy đủ cho FE

### Upload ảnh

```ts
async function uploadImages(files: File[]) {
  if (files.length === 0) return [];
  if (files.length > 5) throw new Error("Chỉ được tối đa 5 ảnh");

  const formData = new FormData();
  files.forEach(file => formData.append("files", file));

  const res = await fetch("/api/uploads/images", {
    method: "POST",
    body: formData
  });

  const json = await res.json();
  if (!json.success) throw new Error(json.message);

  return json.data.files;
}
```

### Upload video chunked

```ts
async function uploadLargeVideo(file: File) {
  const sessionRes = await fetch("/api/uploads/videos/sessions", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      originalFileName: file.name,
      contentType: file.type,
      size: file.size
    })
  });

  const sessionJson = await sessionRes.json();
  if (!sessionJson.success) throw new Error(sessionJson.message);

  const { sessionId, chunkSize } = sessionJson.data;
  const chunks = splitFileIntoChunks(file, chunkSize);

  for (let i = 0; i < chunks.length; i++) {
    const chunkRes = await fetch(`/api/uploads/videos/sessions/${sessionId}/chunks/${i}`, {
      method: "PUT",
      headers: { "Content-Type": "application/octet-stream" },
      body: chunks[i]
    });

    const chunkJson = await chunkRes.json();
    if (!chunkJson.success) throw new Error(chunkJson.message);
  }

  const completeRes = await fetch(`/api/uploads/videos/sessions/${sessionId}/complete`, {
    method: "POST"
  });

  const completeJson = await completeRes.json();
  if (!completeJson.success) throw new Error(completeJson.message);

  return completeJson.data.file;
}
```

---

## 9. Những điều FE không nên làm

- Không tự build URL `/media/...` bằng tay
- Không gửi ảnh qua flow video
- Không gửi video lớn qua `/api/uploads/images`
- Không coi upload ảnh và upload video là cùng một API
- Không assume backend sẽ tự retry chunk lỗi
- Không assume `files[]` là tên field multipart đúng; backend hiện dùng `files`

---

## 10. Kết luận ngắn cho FE agent

- **Ảnh**:
  - gọi `POST /api/uploads/images`
  - gửi `FormData` với field `files`
  - đọc `response.data.files`

- **Video lớn**:
  - gọi `POST /api/uploads/videos/sessions`
  - cắt file theo `chunkSize`
  - upload từng chunk
  - nếu cần thì gọi status để resume
  - cuối cùng gọi `complete`
  - đọc `response.data.file`

- `fileId` dùng cho backend/business reference
- `url` dùng cho preview/render ngay
