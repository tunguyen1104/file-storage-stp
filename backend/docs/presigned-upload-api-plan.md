# Kế hoạch bổ sung API — Presigned URL Upload + Virus Scan

## Mục lục

1. [Tổng quan flow](#1--tổng-quan-flow)
2. [Danh sách API mới](#2--danh-sách-api-mới)
   - [2.1 Khởi tạo presigned upload](#21-post-apiv1uploadspresigned)
   - [2.2 Xác nhận upload hoàn tất](#22-post-apiv1filesfileidconfirm-upload)
   - [2.3 Lấy trạng thái xử lý file](#23-get-apiv1filesfileidstatus)
3. [Trạng thái file (FileStatus)](#3--trạng-thái-file-filestatus)
4. [Use cases mới cần tạo](#4--use-cases-mới-cần-tạo)
5. [Mở rộng ObjectStoragePort](#5--mở-rộng-objectstorageport)
6. [Xử lý Async (Messaging)](#6--xử-lý-async-messaging)
7. [Background Job dọn dẹp PENDING](#7--background-job-dọn-dẹp-pending)
8. [Checklist triển khai theo layer](#8--checklist-triển-khai-theo-layer)
9. [Lưu ý kỹ thuật](#9--lưu-ý-kỹ-thuật)

---

## 1 — Tổng quan flow

```
Client                      System (API)                MinIO               VirusScanner
  │                              │                         │                      │
  │── POST /uploads/presigned ──►│                         │                      │
  │                              │── generatePresignedPUT─►│                      │
  │                              │◄── presigned PUT URL ───│                      │
  │◄── { fileId, uploadUrl } ───│                         │                      │
  │                              │                         │                      │
  │─────── PUT <uploadUrl> ──────────────────────────────►│                      │
  │◄──── 200 OK ────────────────────────────────────────── │                      │
  │                              │                         │                      │
  │── POST /files/{id}/confirm──►│                         │                      │
  │                              │── objectExists? ────────►│                      │
  │                              │◄── yes ─────────────────│                      │
  │                              │── publish VirusScanRequestedEvent ────────────►│
  │◄── { status: SCANNING } ───│                         │                      │
  │                              │                         │                      │
  │  (polling GET /files/{id}/status)                      │                      │
  │                              │                  VirusScanConsumer nhận task   │
  │                              │                         │◄── download file ───│
  │                              │                         │                      │
  │                              │◄── VirusScanCompletedEvent (clean/infected) ──│
  │                              │── SCANNING → ACTIVE / INFECTED                │
  │                              │── cập nhật quota, đẩy task thumbnail/ES index  │
```

**Lý do chọn presigned PUT:**
- Giảm tải băng thông cho server — file bytes không đi qua backend Java.
- MinIO/S3 enforces TTL và content-length natively.
- Client upload trực tiếp; server chỉ kiểm soát qua metadata + confirmation step.

---

## 2 — Danh sách API mới

### 2.1 `POST /api/v1/uploads/presigned`

**Mô tả:** Khởi tạo một phiên upload mới. Hệ thống tạo bản ghi file với trạng thái `PENDING`, sinh sẵn `storage_key`, sau đó yêu cầu MinIO cấp presigned PUT URL.

**Authorization:** Tất cả role đã đăng nhập (`MEMBER`, `ADMIN_TENANT`, `SUPER_ADMIN`).

**Request Body:**

```json
{
  "originalName": "report-2026.pdf",
  "mimeType": "application/pdf",
  "sizeBytes": 2048576,
  "folderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
}
```

| Field | Type | Required | Mô tả |
|-------|------|----------|--------|
| `originalName` | `string` | ✅ | Tên file gốc (không được rỗng) |
| `mimeType` | `string` | ✅ | MIME type khai báo bởi client |
| `sizeBytes` | `long` | ✅ | Kích thước file (bytes), dùng để kiểm tra quota |
| `folderId` | `UUID` | ❌ | Folder đích; nếu null → root của tenant |

**Validation / Business rules:**
1. `folderId` (nếu có) phải thuộc cùng tenant của user → `FOLDER_NOT_FOUND (404)`.
2. User phải có quyền upload vào folder đích → `ACCESS_DENIED (403)`.
3. `sizeBytes` không được vượt quota còn lại của user → `QUOTA_EXCEEDED (422)`.

**Response `201 Created`:**

```json
{
  "success": true,
  "data": {
    "fileId": "a1b2c3d4-...",
    "uploadUrl": "https://minio.internal/file-storage/tenant-id/storage-key?X-Amz-...",
    "expiresAt": "2026-05-01T10:15:00Z"
  }
}
```

| Field | Type | Mô tả |
|-------|------|--------|
| `fileId` | `UUID` | ID bản ghi file (dùng cho bước confirm) |
| `uploadUrl` | `string` | Presigned PUT URL, TTL = 15 phút |
| `expiresAt` | `ISO-8601` | Thời điểm URL hết hạn |

**Error responses:**

| HTTP | ErrorCode | Tình huống |
|------|-----------|------------|
| 400 | `INVALID_PAYLOAD` | Body thiếu field bắt buộc hoặc sizeBytes ≤ 0 |
| 403 | `ACCESS_DENIED` | Không có quyền upload vào folder |
| 404 | `FOLDER_NOT_FOUND` | folderId không tồn tại trong tenant |
| 422 | `QUOTA_EXCEEDED` | sizeBytes vượt quota còn lại |

---

### 2.2 `POST /api/v1/files/{fileId}/confirm-upload`

**Mô tả:** Client gọi sau khi PUT lên MinIO thành công để thông báo server xác minh và kích hoạt virus scan. Server kiểm tra file thực sự tồn tại trên MinIO theo `storage_key`, sau đó chuyển trạng thái `PENDING → SCANNING` và đẩy task vào message queue.

**Authorization:** Chỉ user đã tạo file (owner) hoặc `ADMIN_TENANT`/`SUPER_ADMIN` của cùng tenant.

**Path variable:** `fileId` — UUID của file (trả về từ bước 2.1).

**Request Body:** _(không có)_

**Response `200 OK`:**

```json
{
  "success": true,
  "data": {
    "fileId": "a1b2c3d4-...",
    "status": "SCANNING",
    "message": "File đang được kiểm tra virus. Vui lòng kiểm tra lại sau."
  }
}
```

**Error responses:**

| HTTP | ErrorCode | Tình huống |
|------|-----------|------------|
| 403 | `ACCESS_DENIED` | User không phải owner hoặc không thuộc tenant |
| 404 | `FILE_NOT_FOUND` | fileId không tồn tại hoặc không thuộc tenant |
| 409 | `INVALID_FILE_STATUS` | File không ở trạng thái `PENDING` (đã confirm hoặc đã failed) |
| 422 | `FILE_NOT_FOUND_ON_STORAGE` | File không tồn tại trên MinIO → chuyển sang `UPLOAD_FAILED` |
| 422 | `FILE_METADATA_MISMATCH` | Metadata thực tế trên MinIO (size, content-type) không khớp với khai báo ban đầu → chuyển sang `UPLOAD_FAILED` |

---

### 2.3 `GET /api/v1/files/{fileId}/status`

**Mô tả:** Polling endpoint cho client theo dõi trạng thái xử lý. Tái dùng `GetFileMetadataUseCase`, chỉ trả về subset thông tin.

> **Lưu ý:** Nếu frontend muốn toàn bộ metadata, dùng `GET /api/v1/files/{fileId}` thay thế. Endpoint này tối ưu cho polling thường xuyên.

**Authorization:** Owner hoặc cùng tenant với role đủ quyền.

**Response `200 OK`:**

```json
{
  "success": true,
  "data": {
    "fileId": "a1b2c3d4-...",
    "status": "ACTIVE",
    "originalName": "report-2026.pdf",
    "updatedAt": "2026-05-01T10:16:45Z"
  }
}
```

**Giá trị `status` có thể nhận:**

| Status | Mô tả |
|--------|--------|
| `PENDING` | Đang chờ client upload lên MinIO |
| `SCANNING` | Đang quét virus (async) |
| `ACTIVE` | Upload và scan thành công, file sẵn sàng |
| `UPLOAD_FAILED` | Upload thất bại hoặc presigned URL hết hạn |
| `INFECTED` | Phát hiện virus, file đã bị xóa khỏi MinIO |
| `DELETED` | File đã bị soft-delete bởi user |

---

## 3 — Trạng thái file (FileStatus)

**File cần sửa:** `src/main/java/com/filestorage/domain/model/enums/FileStatus.java`

Bổ sung 2 giá trị mới vào enum hiện có:

```java
public enum FileStatus {
    PENDING,         // Đã tạo bản ghi, chờ upload lên MinIO
    SCANNING,        // [MỚI] Đang quét virus async
    ACTIVE,          // Sẵn sàng sử dụng
    PENDING_DELETE,
    INFECTED,        // [MỚI] Phát hiện virus, file bị xóa vật lý
    UPLOAD_FAILED,   // Upload thất bại / timeout
    DELETED          // Soft-deleted
}
```

**Sơ đồ chuyển trạng thái:**

```
PENDING ──────────────────────► UPLOAD_FAILED  (background job sau 1h hoặc confirm thất bại)
   │
   ▼ (confirm-upload thành công)
SCANNING ─────────────────────► INFECTED       (virus scan phát hiện virus)
   │
   ▼ (scan sạch)
ACTIVE ────────────────────────► DELETED        (user soft-delete)
```

---

## 4 — Use cases mới cần tạo

### 4.1 `InitPresignedUploadUseCase`

**Package:** `com.filestorage.domain.port.in.file`

```java
public interface InitPresignedUploadUseCase {
    PresignedUploadView execute(InitPresignedUploadCommand command);

    record InitPresignedUploadCommand(
        UUID userId,
        UUID tenantId,
        RoleCode role,
        UUID folderId,          // nullable
        String originalName,
        String mimeType,
        long sizeBytes
    ) {}
}
```

**Application service:** `InitPresignedUploadService`
- Validate folder tồn tại và cùng tenant.
- Kiểm tra quyền upload (role + folder ownership nếu cần).
- Kiểm tra quota: `user.storageUsedBytes + sizeBytes ≤ user.maxStorageBytes`.
- Sinh `storageKey = tenantId + "/" + userId + "/" + UUID + "." + extension`.
- Lưu `FileEntity` với status = `PENDING`, `storageKey`, `sizeBytes`.
- Gọi `ObjectStoragePort.generatePresignedPutUrl(storageKey, mimeType, ttl=15min)`.
- Trả về `PresignedUploadView(fileId, uploadUrl, expiresAt)`.

---

### 4.2 `ConfirmUploadUseCase`

**Package:** `com.filestorage.domain.port.in.file`

```java
public interface ConfirmUploadUseCase {
    ConfirmUploadView execute(ConfirmUploadCommand command);

    record ConfirmUploadCommand(
        UUID userId,
        UUID tenantId,
        RoleCode role,
        UUID fileId
    ) {}
}
```

**Application service:** `ConfirmUploadService`
- Load file bằng `fileId`, kiểm tra thuộc tenant và user.
- Kiểm tra status = `PENDING`; nếu không → ném `INVALID_FILE_STATUS`.
- Gọi `ObjectStoragePort.objectExists(storageKey)`:
  - `false` → cập nhật status = `UPLOAD_FAILED`, ném `FILE_NOT_FOUND_ON_STORAGE`.
  - `true` → tiếp tục.
- **[BẢO MẬT]** Gọi `ObjectStoragePort.getObjectMetadata(storageKey)` để lấy metadata thực tế từ MinIO, sau đó so sánh với metadata đã lưu trong `FileEntity`:
  - `actualSizeBytes != file.sizeBytes` → cập nhật status = `UPLOAD_FAILED`, ném `FILE_METADATA_MISMATCH`.
  - `actualContentType` không khớp `file.mimeType` (sau khi normalize) or originalName không khớp → cập nhật status = `UPLOAD_FAILED`, ném `FILE_METADATA_MISMATCH`.
  - Mục đích: ngăn client khai báo sai quota (ví dụ khai báo 1 MB nhưng upload 1 GB), giả mạo MIME type để bypass filter, hoặc upload nội dung không liên quan.
- Cập nhật status = `SCANNING`.
- Publish `VirusScanRequestedEvent(fileId, storageKey, tenantId, userId)` qua `FileEventPublisherPort`.
- Trả về `ConfirmUploadView(fileId, status=SCANNING)`.

---

### 4.3 `GetFileStatusUseCase` _(tái dùng)_

Không cần tạo use case mới. Controller gọi `GetFileMetadataUseCase` rồi chỉ lấy `fileView.status()` và `fileView.updatedAt()` để trả về response gọn.

---

## 5 — Mở rộng ObjectStoragePort

**File cần sửa:** `src/main/java/com/filestorage/domain/port/out/storage/ObjectStoragePort.java`

Bổ sung 2 method mới:

```java
public interface ObjectStoragePort {
    // ... method hiện có (putObject, deleteObject, generatePresignedGetUrl, ...)

    /**
     * Sinh presigned PUT URL để client upload trực tiếp.
     *
     * @param storageKey  đường dẫn object trong bucket
     * @param contentType MIME type khai báo
     * @param ttlMinutes  thời gian hiệu lực URL (tính bằng phút)
     * @return URL dạng string
     */
    String generatePresignedPutUrl(String storageKey, String contentType, int ttlMinutes);

    /**
     * Kiểm tra object có tồn tại trên storage hay không.
     *
     * @param storageKey đường dẫn object trong bucket
     * @return true nếu object tồn tại
     */
    boolean objectExists(String storageKey);

    /**
     * Lấy metadata thực tế của object từ storage (size, content-type, v.v.).
     * Dùng để xác minh file client đã PUT khớp với metadata khai báo lúc init.
     *
     * @param storageKey đường dẫn object trong bucket
     * @return {@link ObjectMetadata} chứa actualSizeBytes và actualContentType
     * @throws ObjectNotFoundException nếu object không tồn tại
     */
    ObjectMetadata getObjectMetadata(String storageKey);

    /**
     * Value object chứa metadata thực tế của object trên storage.
     */
    record ObjectMetadata(long sizeBytes, String contentType) {}
}
```

**Adapter cần cập nhật:** `MinioObjectStorageAdapter` — implement 2 method trên dùng MinIO SDK:
- `generatePresignedPutUrl`: dùng `MinioClient.getPresignedObjectUrl(...)` với method = `PUT`.
- `objectExists`: dùng `MinioClient.statObject(...)`, bắt exception để trả về `false`.
- `getObjectMetadata`: dùng `MinioClient.statObject(...)` để lấy `StatObjectResponse`, ánh xạ sang `ObjectMetadata(size, contentType)`.

---

## 6 — Xử lý Async (Messaging)

### 6.1 Events mới

| Event | Publisher | Consumer |
|-------|-----------|----------|
| `VirusScanRequestedEvent` | `ConfirmUploadService` | `VirusScanConsumer` |
| `VirusScanCompletedEvent` | `VirusScanConsumer` | `VirusScanResultHandler` |
| `FileReadyEvent` (đã có hoặc tạo mới) | `VirusScanResultHandler` | Thumbnail, Elasticsearch indexer |

Phần xử lý Elasticsearch indexer chưa cần implement ngay, để sau này tôi học xong phần Elasticsearch có thể bổ sung thêm.

### 6.2 `VirusScanRequestedEvent`

```java
public record VirusScanRequestedEvent(
    UUID fileId,
    String storageKey,
    UUID tenantId,
    UUID userId
) {}
```

**Queue:** `file.virus-scan.request` (RabbitMQ) hoặc tương đương.

### 6.3 `VirusScanConsumer` (adapter in/messaging)

- Nhận `VirusScanRequestedEvent` từ queue.
- Tải file từ MinIO theo `storageKey`.
- Scan bằng ClamAV (xem mục [9](#9--lưu-ý-kỹ-thuật) về ClamAV adapter).
- Publish `VirusScanCompletedEvent(fileId, storageKey, tenantId, userId, isInfected)`.

### 6.4 `VirusScanResultHandler` (application service hoặc use case)

**Khi `isInfected = true`:**
1. Cập nhật status = `INFECTED`.
2. Gọi `ObjectStoragePort.deleteObject(storageKey)` xóa file vật lý.
3. Không cộng `storageUsedBytes` (file đã bị xóa).
4. Ghi audit log.

**Khi `isInfected = false`:**
1. Cập nhật status = `ACTIVE`.
2. Cộng `sizeBytes` vào `user.storageUsedBytes` và `tenant.storageUsedBytes`.
3. Publish `FileReadyEvent` để trigger thumbnail generation + Elasticsearch index.
4. Ghi audit log.

---

## 7 — Background Job dọn dẹp PENDING

**Class:** `PendingFileCleanupJob`  
**Package:** `com.filestorage.adapter.out.scheduling`

```java
@Component
@RequiredArgsConstructor
public class PendingFileCleanupJob {

    private final FileRepository fileRepository;
    private final ObjectStoragePort objectStoragePort;

    @Scheduled(fixedDelay = 15, timeUnit = TimeUnit.MINUTES)
    @SchedulerLock(name = "pendingFileCleanup", lockAtLeastFor = "5m", lockAtMostFor = "14m")
    public void cleanupStalePendingFiles() {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
        List<FileEntity> staleFiles = fileRepository
            .findByStatusAndCreatedAtBefore(FileStatus.PENDING, cutoff);

        for (FileEntity file : staleFiles) {
            file.setStatus(FileStatus.UPLOAD_FAILED);
            fileRepository.save(file);
            // Tùy chọn: xóa object nếu có trên MinIO (trường hợp partial upload)
            objectStoragePort.deleteObjectIfExists(file.getStorageKey());
        }
    }
}
```

**Repository method cần thêm:**

```java
List<FileEntity> findByStatusAndCreatedAtBefore(FileStatus status, Instant cutoff);
```

**Lưu ý:** Dùng `@SchedulerLock` (ShedLock) để đảm bảo job chỉ chạy trên 1 node trong môi trường multi-instance.

---

## 8 — Checklist triển khai theo layer

### 8.1 Domain layer

- [ ] `FileStatus.java` — thêm `SCANNING`, `INFECTED`
- [ ] `PresignedUploadView.java` — domain view mới (fileId, uploadUrl, expiresAt)
- [ ] `ConfirmUploadView.java` — domain view mới (fileId, status)
- [ ] `InitPresignedUploadUseCase.java` — port in mới
- [ ] `ConfirmUploadUseCase.java` — port in mới
- [ ] `ObjectStoragePort.java` — thêm `generatePresignedPutUrl`, `objectExists`, `getObjectMetadata`
- [ ] `FileEventPublisherPort.java` — thêm method publish `VirusScanRequestedEvent` (hoặc tạo port mới `VirusScanEventPublisherPort`)
- [ ] `VirusScanRequestedEvent.java` — domain event mới
- [ ] `VirusScanCompletedEvent.java` — domain event mới
- [ ] `VirusScanPort.java` — port out mới, interface `boolean scan(InputStream fileStream)`

### 8.2 Application layer (use cases / services)

- [ ] `InitPresignedUploadService.java` — implement `InitPresignedUploadUseCase`
- [ ] `ConfirmUploadService.java` — implement `ConfirmUploadUseCase`
- [ ] `VirusScanResultHandler.java` — xử lý kết quả scan (service hoặc use case)

### 8.3 Adapter — REST (in)

- [ ] `FileController.java` — thêm 3 endpoint mới:
  - `POST /api/v1/uploads/presigned`
  - `POST /api/v1/files/{fileId}/confirm-upload`
  - `GET /api/v1/files/{fileId}/status`
- [ ] `PresignedUploadInitRequest.java` — request DTO
- [ ] `PresignedUploadInitResponse.java` — response DTO
- [ ] `ConfirmUploadResponse.java` — response DTO
- [ ] `FileStatusResponse.java` — response DTO
- [ ] `FileRestMapper.java` — thêm mapping cho các view/DTO mới

### 8.4 Adapter — Persistence (out)

- [ ] `FileJpaRepository.java` / `FileRepository.java` — thêm query `findByStatusAndCreatedAtBefore`
- [ ] Migration SQL (nếu cần thêm cột, e.g. `confirmed_at`)

### 8.5 Adapter — Messaging (in + out)

- [ ] `VirusScanEventPublisher.java` — implement `FileEventPublisherPort`, publish tới RabbitMQ
- [ ] `VirusScanConsumer.java` — `@RabbitListener`, nhận task, gọi `VirusScanPort`, publish kết quả
- [ ] RabbitMQ config — khai báo queue `file.virus-scan.request`, `file.virus-scan.result`

### 8.6 Adapter — Storage (out)

- [ ] `MinioObjectStorageAdapter.java` — implement `generatePresignedPutUrl`, `objectExists`, `getObjectMetadata`

### 8.7 Adapter — Virus Scanner (out)

- [ ] `ClamAvVirusScanAdapter.java` — implement `VirusScanPort` (xem lưu ý ClamAV bên dưới)

### 8.8 Adapter — Scheduling (out)

- [ ] `PendingFileCleanupJob.java` — `@Scheduled` + `@SchedulerLock`

### 8.9 Configuration

- [ ] `application.yml` — thêm config:
  ```yaml
  app:
    upload:
      presigned-put-ttl-minutes: 15
      pending-cleanup-threshold-hours: 1
    virus-scan:
      clamav:
        host: localhost
        port: 3310
        timeout-ms: 30000
  ```

---

## 9 — Lưu ý kỹ thuật

### ClamAV Integration

Có 2 lựa chọn cho `ClamAvVirusScanAdapter` (Using REST wrapper):

| Lựa chọn | Thư viện | Ưu điểm | Nhược điểm |
|----------|---------|---------|------------|
| **Java socket client** | [`clamav4j`](https://github.com/solarlabs/clamav4j) hoặc tự implement INSTREAM protocol | Không cần sidecar HTTP | Cần handle TCP socket, timeout |
| **REST wrapper** | [ClamAV REST API](https://github.com/benzino77/clamav-rest-api) (Docker sidecar) | Đơn giản, stateless | Thêm 1 container |

**Khuyến nghị:** Dùng REST wrapper trong Docker Compose để dễ swap. Interface `VirusScanPort` giữ nguyên, chỉ đổi adapter.

### Notification cho client

Activity diagram đề cập user nhận thông báo trạng thái. 3 lựa chọn:

| Cơ chế | Độ phức tạp | Khuyến nghị |
|--------|------------|------------|
| **Polling** `GET /files/{id}/status` | Thấp | ✅ Triển khai trước |
| **SSE** (Server-Sent Events) | Trung bình | Nâng cấp sau khi polling ổn định |
| **WebSocket** | Cao | Cân nhắc nếu cần real-time nhiều loại sự kiện |

**Mặc định:** Polling. Client poll `GET /files/{fileId}/status` mỗi 2-3 giây cho đến khi status = `ACTIVE`, `INFECTED`, hoặc `UPLOAD_FAILED`.

### Bảo mật presigned URL

- Presigned PUT URL chứa signature của MinIO — chỉ dùng được 1 lần trong TTL.
- Server **không lưu** URL vào DB, chỉ lưu `storageKey` và `expiresAt`.
- Nếu client không confirm trước `expiresAt`, background job sẽ dọn dẹp sau 1h.
- Cân nhắc thêm field `Content-Length-Range` condition trong presigned URL để enforce `sizeBytes` khai báo.

### Quota: khi nào trừ?

- **Không trừ quota khi tạo PENDING** — tránh trừ oan khi upload thất bại.
- **Chỉ cộng `storageUsedBytes` sau khi scan sạch** (status → `ACTIVE`).
- **Kiểm tra quota dựa trên `sizeBytes` khai báo** ở bước `InitPresignedUpload` để từ chối sớm.

