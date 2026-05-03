# Upload — Tài liệu kỹ thuật

## Mục lục

1. [Presigned Upload (file nhỏ)](#1--presigned-upload-file-nhỏ)
2. [Multipart Upload (file lớn)](#2--multipart-upload-file-lớn)
3. [Virus Scan Pipeline (dùng chung)](#3--virus-scan-pipeline-dùng-chung)
4. [Config](#4--config)

---

## 1 — Presigned Upload (file nhỏ)

### Tổng quan

Dành cho file thông thường. Client upload trực tiếp lên MinIO qua **presigned PUT URL** (TTL 15 phút). Backend chỉ cấp phát URL, xác minh metadata và kích hoạt virus scan.

### Flow

```
Client → POST /uploads/presigned          → Backend tạo File(PENDING) + trả về presigned URL
Client → PUT  <presignedUrl>              → Client upload thẳng lên MinIO
Client → POST /files/{id}/confirm-upload  → Backend xác minh metadata (size, content-type)
                                            PENDING → SCANNING
                                            Publish VirusScanRequestedEvent → RabbitMQ
VirusScanConsumer → scan ClamAV          → Publish VirusScanCompletedEvent
VirusScanResultHandler                    → SCANNING → ACTIVE (sạch) / INFECTED (có virus)
Client → GET  /files/{id}/status          → Polling trạng thái
```

### API

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| POST | `/api/v1/uploads/presigned` | Khởi tạo, nhận `fileId` + `uploadUrl` |
| POST | `/api/v1/files/{fileId}/confirm-upload` | Xác nhận upload xong, kích hoạt virus scan |
| GET  | `/api/v1/files/{fileId}/status` | Polling trạng thái |

### Trạng thái file

```
PENDING → SCANNING → ACTIVE
                   → INFECTED
        → UPLOAD_FAILED   (confirm thất bại hoặc background job dọn sau 1h)
ACTIVE  → DELETED         (user soft-delete)
```

### Các thành phần đã thêm / sửa

**Domain**
- `FileStatus.java` — thêm `SCANNING`, `INFECTED`
- `PresignedUploadView`, `ConfirmUploadView` — domain views
- `InitPresignedUploadUseCase`, `ConfirmUploadUseCase` — port in
- `ObjectStoragePort` — thêm `generatePresignedPutUrl`, `objectExists`, `getObjectMetadata`
- `VirusScanPort` — port out cho ClamAV (`boolean scan(InputStream)`)
- `VirusScanEventPublisherPort` — port out publish event
- `VirusScanRequestedEvent`, `VirusScanCompletedEvent` — domain events

**Application**
- `InitPresignedUploadService` — validate folder, `SELECT FOR UPDATE` check quota, tạo `File(PENDING)`, sinh presigned PUT URL
- `ConfirmUploadService` — xác minh file tồn tại trên MinIO, delegate verify size + MIME + scan routing sang `UploadStorageVerifier`; chuyển `PENDING → SCANNING`, publish event
- `UploadStorageVerifier` *(dùng chung presigned & multipart)* — `verifySizeOrFail` (StatObject), `verifyMimeOrFail` (Tika, 8 KB đầu), `routeToScanOrActivate` (bypass hoặc publish event)
- `FileUploadValidator` — `validateMimeType` (whitelist), `rejectIfMimeSpoofed` (Tika), `shouldBypassScan` (size + MIME gate)
- `VirusScanResultHandler` — sạch → `ACTIVE` + atomic quota UPDATE; nhiễm → `INFECTED` + xóa MinIO object

**Adapter — REST**
- `FileController` — 3 endpoint mới, tái dùng `GetFileMetadataUseCase` cho status endpoint
- DTOs: `PresignedUploadInitRequest`, `PresignedUploadInitResponse`, `ConfirmUploadResponse`, `FileStatusResponse`

**Adapter — Persistence**
- `FileJpaRepository` — thêm `findByStatusAndCreatedAtBefore`, các query `JOIN FETCH uploadedBy` tránh N+1
- `UserJpaRepository` — thêm atomic UPDATE quota method

**Adapter — Storage**
- `MinioStorageAdapter` — thêm `generatePresignedPutUrl`, `objectExists`, `getObjectMetadata`, **`downloadPartial(storageKey, maxBytes)`** (dùng `GetObjectArgs.offset(0).length(n)` để chỉ lấy N byte đầu)

**Adapter — Virus Scan**
- `ClamAvVirusScanAdapter` — implement `VirusScanPort` qua ClamAV REST sidecar

**Adapter — Messaging**
- `VirusScanEventPublisher`, `VirusScanConsumer`, `VirusScanRabbitMQConfig` — queue `file.virus-scan.request` / `file.virus-scan.result`

**Adapter — Scheduling**
- `PendingFileCleanupJob` — mỗi 15 phút (ShedLock), file `PENDING` quá 1h → `UPLOAD_FAILED` + xóa MinIO object

### Lưu ý

- **Quota:** Không trừ khi tạo `PENDING`. Chỉ cộng sau scan sạch bằng atomic UPDATE.
- **Xác minh metadata:** `StatObject` từ MinIO so với khai báo — ngăn upload file lớn hơn quota đã bypass. `sizeBytes` do MinIO server đếm thực tế, không thể fake.
- **MIME whitelist:** `FileUploadValidator.validateMimeType()` kiểm tra tại `InitPresignedUploadService` (early-exit, client-declared). Sau đó `UploadStorageVerifier.verifyMimeOrFail()` re-verify bằng Apache Tika với 8 KB đầu file — chống spoofing (e.g. `evil.exe` → `report.pdf`).
- **Partial download:** `ObjectStoragePort.downloadPartial(storageKey, 8192)` chỉ lấy 8 KB từ MinIO thay vì toàn bộ file — tránh load file lớn vào RAM chỉ để detect MIME.
- **Presigned URL:** TTL 15 phút, không lưu DB.

---

## 2 — Multipart Upload (file lớn)

### Tổng quan

Dành cho file lớn (> 100 MB). Client chia file thành nhiều chunks, upload song song từng chunk trực tiếp lên MinIO. Backend chỉ orchestrate — không có bytes đi qua backend. MinIO ghép file server-side.

### Flow

```
Client → POST /uploads/multipart/init              → Backend tạo File(PENDING) + Session(IN_PROGRESS)
                                                      Gọi MinIO initiateMultipart → nhận uploadId
                                                      Cache session Redis 24h
Client → GET  /uploads/multipart/{id}/part-url     → Backend sinh presigned PUT URL (TTL 30 phút)
Client → PUT  <presignedPartUrl>                   → Upload chunk thẳng lên MinIO
                                                      MinIO trả ETag trong response header
Client → POST /uploads/multipart/{id}/parts/{n}/complete → Ghi ETag vào uploaded_parts JSONB + Redis
         ... lặp / song song cho các parts còn lại ...
Client → POST /uploads/multipart/{id}/complete     → Gọi MinIO completeMultipart (ghép server-side)
                                                      File: PENDING → SCANNING
                                                      Session: IN_PROGRESS → COMPLETED
                                                      Publish VirusScanRequestedEvent → RabbitMQ
VirusScanConsumer → scan ClamAV                   → Publish VirusScanCompletedEvent
VirusScanResultHandler                             → SCANNING → ACTIVE (sạch) / INFECTED (có virus)
Client → GET  /api/v1/files/{fileId}/status        → Polling trạng thái
```

**Resume upload:** Client mất kết nối → gọi `GET /uploads/multipart/{uploadId}` → lấy danh sách parts đã upload → tiếp tục từ part còn thiếu.

### API

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| POST | `/api/v1/uploads/multipart/init` | Khởi tạo session, nhận `uploadId` + `fileId` |
| GET | `/api/v1/uploads/multipart/{uploadId}/part-url?partNumber=N` | Lấy presigned PUT URL cho một part |
| POST | `/api/v1/uploads/multipart/{uploadId}/parts/{partNumber}/complete` | Báo cáo part đã upload + ETag |
| POST | `/api/v1/uploads/multipart/{uploadId}/complete` | Hoàn tất — ghép file, kích hoạt virus scan |
| DELETE | `/api/v1/uploads/multipart/{uploadId}` | Huỷ upload |
| GET | `/api/v1/uploads/multipart/{uploadId}` | Lấy trạng thái session (resume support) |
| GET | `/api/v1/files/{fileId}/status` | Polling trạng thái file sau complete |

### Trạng thái

```
File:    PENDING → SCANNING → ACTIVE
                            → INFECTED
                → UPLOAD_FAILED   (abort hoặc cleanup job sau 24h)

Session: IN_PROGRESS → COMPLETED  (POST /complete thành công)
                     → ABORTED    (DELETE hoặc cleanup job sau 24h)
```

### Các thành phần đã thêm / sửa

**Domain**
- `MultipartUploadStatus.java` — enum `IN_PROGRESS`, `COMPLETED`, `ABORTED`
- `MultipartUploadInitView`, `PartPresignedUrlView`, `PartCompletionView`, `MultipartCompleteView`, `MultipartSessionView` — domain views
- `InitMultipartUploadUseCase`, `GetPartPresignedUrlUseCase`, `CompletePartUploadUseCase`, `CompleteMultipartUploadUseCase`, `AbortMultipartUploadUseCase`, `GetMultipartUploadSessionUseCase` — port in
- `ObjectStoragePort` — thêm `initiateMultipartUpload`, `generatePresignedPartUrl`, `completeMultipartUpload`, `abortMultipartUpload`
- `CachePort` — thêm `cacheMultipartSession`, `getMultipartSession`, `evictMultipartSession`

**Application**
- `InitMultipartUploadService` — validate input, `SELECT FOR UPDATE` check quota (in-flight PENDING/SCANNING), validate folder, gọi MinIO `initiateMultipartUpload`, tạo `File(PENDING)` + `Session(IN_PROGRESS)`, cache Redis 24h
- `GetPartPresignedUrlService` — load từ Redis (fallback DB), validate `IN_PROGRESS` + chưa hết hạn + ownership, sinh presigned PUT URL
- `CompletePartUploadService` — validate ownership, append `PartInfo(partNumber, etag)` vào JSONB, cập nhật DB + Redis
- `CompleteMultipartUploadService` — validate đủ parts, gọi MinIO `completeMultipartUpload`, `IN_PROGRESS → COMPLETED`, xoá Redis, delegate verify size + MIME + scan routing sang `UploadStorageVerifier`
- `AbortMultipartUploadService` — gọi MinIO `abortMultipartUpload`, `UPLOAD_FAILED` + `ABORTED`, xoá Redis
- `GetMultipartUploadSessionService` — trả về session kèm `uploadedParts` để resume
- `UploadStorageVerifier` *(dùng chung với presigned)* — `verifySizeOrFail`, `verifyMimeOrFail`, `routeToScanOrActivate`
- `VirusScanResultHandler` — **tái dùng hoàn toàn** từ presigned upload

**Adapter — REST**
- `MultipartUploadController` — 6 endpoints, dùng `SecurityContextUtil.getCurrentUserOrThrow()` / `parseRoleOrThrow()`
- Request DTOs: `InitMultipartUploadRequest`, `CompletePartRequest`, `CompleteMultipartRequest` (`@Getter` + `@NoArgsConstructor`)
- Response DTOs: `MultipartInitResponse`, `PartUrlResponse`, `PartCompletionResponse`, `MultipartCompleteResponse`, `MultipartSessionResponse`
- `MultipartUploadRestMapper` — MapStruct mapper

**Adapter — Persistence**
- `MultipartUploadSession.java` — JPA entity, `uploadedParts` lưu JSONB (`List<PartInfo>`)
- `MultipartUploadSessionJpaRepository` — `findByUploadIdAndTenantId` dùng `JOIN FETCH initiatedBy` tránh N+1; `findByStatusAndExpiresAtBefore` cho cleanup job

**Adapter — Storage**
- `MinioStorageAdapter` — implement 4 methods multipart dùng `MinioAsyncClient`; bắt `InterruptedException` restore interrupt flag, `ExecutionException` unwrap cause
- `StorageConfig` — thêm bean `MinioAsyncClient` riêng cho multipart

**Adapter — Cache**
- `RedisCache` — thêm 3 methods multipart session, key `multipart:session:{uploadId}`, TTL 24h

**Adapter — Messaging**
- `VirusScanConsumer`, `VirusScanEventPublisher`, `VirusScanRabbitMQConfig` — **tái dùng hoàn toàn**, không thay đổi

**Adapter — Scheduling**
- `ExpiredMultipartSessionCleanupJob` — mỗi 30 phút (ShedLock), session `IN_PROGRESS` quá `expiresAt` → MinIO abort + `UPLOAD_FAILED` + `ABORTED` + xoá Redis

### Lưu ý

- **Quota:** `SELECT FOR UPDATE` tại `init` tránh race condition. Chỉ cộng quota sau scan sạch bằng atomic UPDATE.
- **Concurrent parts:** `CompletePartUploadService` dùng optimistic locking (`@Version`) tránh ghi đè JSONB.
- **MIME whitelist:** `FileUploadValidator.validateMimeType()` kiểm tra tại `InitMultipartUploadService` (early-exit). `UploadStorageVerifier.verifyMimeOrFail()` re-verify bằng Tika sau khi MinIO hoàn tất ghép file — chỉ download 8 KB đầu, không load cả file vào RAM.
- **Size verify:** `UploadStorageVerifier.verifySizeOrFail()` gọi `getObjectMetadata` sau khi MinIO assemble — `sizeBytes` do MinIO đếm server-side, client không thể fake.
- **ETag:** Lấy từ response header MinIO sau PUT chunk. Client gửi nguyên xi (kể cả dấu `"` bao quanh).
- **Không cần confirm-upload:** Multipart hoàn tất ngay tại `POST /complete`, khác với presigned single-file.
- **MinIO constraints:** Part tối thiểu 5 MB (trừ part cuối), tối đa 10.000 parts, file tối đa 5 TB.

---

## 3 — Virus Scan Pipeline (dùng chung)

Cả hai flow đều dùng chung toàn bộ pipeline virus scan:

| Component | Vai trò |
|-----------|---------|
| `VirusScanRequestedEvent` | Domain event kích hoạt scan |
| `VirusScanEventPublisher` | Gửi event vào RabbitMQ queue `file.virus-scan.request` |
| `VirusScanConsumer` | `@RabbitListener` — tải file từ MinIO, gọi ClamAV, publish kết quả |
| `VirusScanResultHandler` | Nhận `VirusScanCompletedEvent` — `SCANNING → ACTIVE/INFECTED`, atomic quota UPDATE |
| `ClamAvVirusScanAdapter` | Implement `VirusScanPort` qua ClamAV REST sidecar |

---

## 4 — Config

```yaml
app:
  upload:
    presigned-put-ttl-minutes: 15
    pending-cleanup-threshold-hours: 1
    multipart:
      part-url-ttl-minutes: 30
      session-ttl-hours: 24
      cleanup-interval-minutes: 30

    # Whitelist — only these MIME types are accepted at upload init time
    allowed-mime-types:
      - image/jpeg
      - image/png
      - image/gif
      - image/webp
      - image/svg+xml
      - application/pdf
      - application/msword
      - application/vnd.openxmlformats-officedocument.wordprocessingml.document
      - application/vnd.ms-excel
      - application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
      - text/plain
      - text/csv
      - video/mp4
      - video/quicktime
      - video/x-msvideo
      - audio/mpeg
      - audio/wav
      - audio/flac
      - audio/ogg

    virus-scan:
      clamav:
        host: localhost
        port: 3310
        timeout-ms: 30000
      # Files larger than this (MB) in a bypass-exempt MIME type skip ClamAV entirely
      bypass-scan-size-mb: 50
      bypass-mime-types:
        - video/mp4
        - video/quicktime
        - video/x-msvideo
        - audio/mpeg
        - audio/wav
        - audio/flac
        - audio/ogg
```

---

## 5 — Security notes

- **Never trust client-declared MIME alone** — whitelist check tại init là early-exit guard. `UploadStorageVerifier.verifyMimeOrFail()` luôn re-verify bằng Apache Tika sau khi file đã lên MinIO.
- **Partial download cho Tika** — chỉ tải 8 KB đầu (`downloadPartial`) thay vì toàn bộ file. Tika đọc magic bytes, không cần toàn bộ nội dung.
- **`sizeBytes` không thể fake** — MinIO tính từ bytes thực nhận được (server-side). `getObjectMetadata` là source of truth, khác với `Content-Type` do client tự khai.
- **Bypass là size + MIME gated** — `video/mp4` dưới 50 MB vẫn bị scan bình thường.
- **ZIP/EXE bị chặn hoàn toàn** — `application/zip`, `application/x-msdownload`, `application/x-executable` không có trong whitelist, bị reject tại init trước khi bytes chạm MinIO.
- **MIME spoof detected sau upload** — file bị `UPLOAD_FAILED` và object bị purge khỏi MinIO ngay lập tức.

