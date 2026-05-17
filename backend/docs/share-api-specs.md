# Share API Specs

Base path: `/api/v1/shares`  
Authentication: Bearer token required on all endpoints.

---

## Enums

| Enum | Values |
|---|---|
| `ShareStatus` | `ACTIVE`, `EXPIRED`, `REVOKED` |
| `ShareRecipientType` | `USER`, `TENANT` |
| `PermissionFile` | `VIEW`, `EDIT` |
| `ShareRevokeScope` | `GRANTED`, `RECEIVED`, `BOTH` |

---

## Common Response Shapes

### ShareResponse
```json
{
  "id": "uuid",
  "tenantId": "uuid",
  "resourceType": "file | folder",
  "fileId": "uuid | null",
  "folderId": "uuid | null",
  "permission": "VIEW | EDIT",
  "status": "ACTIVE | EXPIRED | REVOKED",
  "recipientType": "USER | TENANT",
  "sharedWithUser": { "id": "uuid", "email": "string", "fullName": "string" },
  "sharedWithTenant": { "id": "uuid", "name": "string" },
  "sharedBy": { "id": "uuid", "fullName": "string" },
  "expiresAt": "ISO-8601",
  "createdAt": "ISO-8601",
  "revokedAt": "ISO-8601 | null"
}
```
> `sharedWithUser` và `sharedWithTenant` luôn có một cái null tùy `recipientType`.

### ShareWithResourceResponse
Giống `ShareResponse` nhưng bỏ `tenantId`, thêm:
```json
"resource": {
  "type": "file | folder",
  "id": "uuid",
  "name": "string",
  "mimeType": "string | null",
  "sizeBytes": "number | null"
}
```

### SharedWithMeResponse
```json
{
  "shareId": "uuid",
  "resourceType": "file | folder",
  "permission": "VIEW | EDIT",
  "status": "ACTIVE | EXPIRED | REVOKED",
  "recipientType": "USER | TENANT",
  "expiresAt": "ISO-8601",
  "sharedBy": { "id": "uuid", "fullName": "string" },
  "resource": { "type", "id", "name", "mimeType", "sizeBytes" }
}
```

### ShareExpirationResponse
```json
{
  "id": "uuid",
  "status": "ACTIVE | EXPIRED | REVOKED",
  "expiresAt": "ISO-8601",
  "revokedAt": "ISO-8601 | null",
  "updatedAt": "ISO-8601"
}
```

---

## 1. Share File với User

```
POST /api/v1/shares/files
```

**Chức năng:** Chia sẻ một file cụ thể cho một user khác trong cùng tenant bằng email. Người nhận sẽ có quyền xem (`VIEW`) file trong khoảng thời gian được chỉ định.

**Sử dụng khi nào:**
- User muốn chia sẻ file cho đồng nghiệp trong cùng tổ chức để xem.
- Chia sẻ tài liệu nội bộ có thời hạn — hết hạn tự động thu hồi quyền truy cập.

**Roles:** Tất cả (chỉ resource owner được share).

**Request body:**
```json
{
  "fileId": "uuid",
  "sharedWithUserEmail": "string (email, required)",
  "expireTime": "number (phút, > 0, required)"
}
```

**Rules:**
- Actor phải là owner của file.
- File phải có status `ACTIVE`.
- Không được tự share cho chính mình.
- Không được share cho user khác tenant.
- Không tạo share trùng (cùng file + cùng user đang `ACTIVE`).

**Response:** `201 ShareResponse`

**Errors:**

| Code | HTTP | Mô tả |
|---|---|---|
| 1021 | 404 | File không tồn tại |
| 1023 | 409 | File không ở trạng thái ACTIVE |
| 1043 | 403 | Actor không phải owner của file |
| 1044 | 403 | Không được tự share cho mình |
| 1004 | 404 | Email không tồn tại |
| 1046 | 409 | Share đã tồn tại (ACTIVE) |
| 1042 | 400 | expireTime không hợp lệ |

---

## 2. Share Folder với User

```
POST /api/v1/shares/folders
```

**Chức năng:** Chia sẻ một folder cho một user khác trong cùng tenant. Khác với share file, người dùng có thể chỉ định mức quyền `VIEW` hoặc `EDIT`. Quyền truy cập folder tự động kéo theo toàn bộ nội dung bên trong (các file/subfolder con kế thừa quyền).

**Sử dụng khi nào:**
- Chia sẻ cả một thư mục dự án cho đồng nghiệp thay vì share từng file riêng lẻ.
- Cấp quyền `EDIT` để người nhận có thể chỉnh sửa tài liệu trong folder.
- Cấp quyền `VIEW` để người nhận chỉ xem, không sửa được.

**Roles:** Tất cả (chỉ resource owner được share).

**Request body:**
```json
{
  "folderId": "uuid",
  "sharedWithUserEmail": "string (email, required)",
  "permission": "VIEW | EDIT",
  "expireTime": "number (phút, > 0, required)"
}
```

**Rules:** Tương tự Share File với User (thay file → folder).

**Response:** `201 ShareResponse`

**Errors:**

| Code | HTTP | Mô tả |
|---|---|---|
| 1022 | 404 | Folder không tồn tại |
| 1023 | 409 | File không ở trạng thái ACTIVE |
| 1043 | 403 | Actor không phải owner của folder |
| 1044 | 403 | Không được tự share cho mình |
| 1004 | 404 | Email không tồn tại |
| 1046 | 409 | Share đã tồn tại (ACTIVE) |
| 1042 | 400 | expireTime không hợp lệ |

---

## 3. Share File với Tenant

```
POST /api/v1/shares/files/tenant
```

**Chức năng:** Chia sẻ file ở cấp độ tenant thay vì từng user. Toàn bộ thành viên của tenant nhận được quyền truy cập file mà không cần share từng người.

Có 3 hành vi khác nhau tùy role và `tenantId`:
- **TENANT_ADMIN:** Chỉ share được với tenant của chính mình.
- **SUPER_ADMIN + `tenantId`:** Share với một tenant cụ thể.
- **SUPER_ADMIN + không truyền `tenantId`:** Share với **tất cả tenant** trong hệ thống (`allTenants = true`).

**Sử dụng khi nào:**
- TENANT_ADMIN muốn mở quyền xem một file cho toàn bộ thành viên trong tổ chức, thay vì gửi link cho từng người.
- SUPER_ADMIN muốn phân phối tài liệu hệ thống (chính sách, template, hướng dẫn) cho một tenant hoặc tất cả tenant cùng lúc.

**Roles:** `TENANT_ADMIN`, `SUPER_ADMIN`.

**Request body:**
```json
{
  "resourceId": "uuid",
  "expireTime": "number (phút, > 0, required)",
  "tenantId": "uuid (optional)"
}
```

**Rules theo role:**

| Role | `tenantId` | Hành vi |
|---|---|---|
| `TENANT_ADMIN` | ≠ actorTenantId | 403 CROSS_TENANT_SHARE_NOT_ALLOWED |
| `TENANT_ADMIN` | null hoặc actorTenantId | Share với tenant của chính mình |
| `SUPER_ADMIN` | uuid cụ thể | Share với tenant được chỉ định |
| `SUPER_ADMIN` | null | Share với **tất cả tenant** |

- Actor phải là owner của file.
- File phải có status `ACTIVE`.
- Không tạo share trùng.

**Response:** `201 ShareResponse`
> `recipientType = TENANT`. `sharedWithUser = null`.  
> Khi `allTenants = true`: `sharedWithTenant = null`.

**Errors:**

| Code | HTTP | Mô tả |
|---|---|---|
| 1021 | 404 | File không tồn tại |
| 1023 | 409 | File không ở trạng thái ACTIVE |
| 1043 | 403 | Actor không phải owner |
| 1045 | 403 | TENANT_ADMIN truyền tenantId khác tenant mình |
| 1014 | 404 | tenantId không tồn tại (SUPER_ADMIN) |
| 1046 | 409 | Share đã tồn tại |
| 1042 | 400 | expireTime không hợp lệ |

---

## 4. Share Folder với Tenant

```
POST /api/v1/shares/folders/tenant
```

**Chức năng:** Tương tự API 3 nhưng áp dụng cho folder. Toàn bộ nội dung bên trong folder (file, subfolder) đều được kế thừa quyền truy cập.

**Sử dụng khi nào:**
- TENANT_ADMIN muốn mở một thư mục tài liệu chung cho toàn bộ nhân viên trong tổ chức.
- SUPER_ADMIN muốn cung cấp bộ template/tài liệu hệ thống dưới dạng folder cho tất cả tenant hoặc một tenant cụ thể.

**Roles:** `TENANT_ADMIN`, `SUPER_ADMIN`.

**Request body:** Giống API 3 (`resourceId` là folderId).

**Rules:** Tương tự Share File với Tenant (thay file → folder).

**Response:** `201 ShareResponse`

**Errors:**

| Code | HTTP | Mô tả |
|---|---|---|
| 1022 | 404 | Folder không tồn tại |
| 1043 | 403 | Actor không phải owner |
| 1045 | 403 | TENANT_ADMIN truyền tenantId khác tenant mình |
| 1014 | 404 | tenantId không tồn tại (SUPER_ADMIN) |
| 1046 | 409 | Share đã tồn tại |
| 1042 | 400 | expireTime không hợp lệ |

---

## 5. Gia hạn Share

```
PATCH /api/v1/shares/{shareId}/expiration
```

**Chức năng:** Gia hạn thời gian của một share. Hành vi khác nhau tùy trạng thái hiện tại:
- Share đang `ACTIVE`: cộng dồn thêm thời gian vào `expiresAt` hiện tại (không reset về now).
- Share đã `EXPIRED`: kích hoạt lại từ thời điểm hiện tại + thời gian gia hạn, đổi status về `ACTIVE`.
- Share đã `REVOKED`: không cho gia hạn — phải tạo share mới.

**Sử dụng khi nào:**
- User muốn kéo dài thêm thời gian truy cập mà không cần tạo share mới (tránh phải gửi lại link).
- Reactivate một share vừa hết hạn khi cộng tác viên cần tiếp tục làm việc.

**Roles:** Tất cả (chỉ người tạo share).

**Path param:** `shareId` (uuid)

**Request body:**
```json
{
  "expireTime": "number (phút, > 0, required)"
}
```

**Response:** `200 ShareExpirationResponse`

**Errors:**

| Code | HTTP | Mô tả |
|---|---|---|
| 1047 | 404 | Share không tồn tại |
| 1043 | 403 | Không phải người tạo share |
| 1048 | 409 | Share đã REVOKED, cần tạo share mới |
| 1042 | 400 | expireTime không hợp lệ |

---

## 6. Lấy danh sách share do tôi tạo

```
GET /api/v1/shares/by-me
```

**Chức năng:** Trả về danh sách tất cả các share mà user hiện tại đã tạo, kèm thông tin resource (tên file/folder, dung lượng, MIME type). Có thể lọc theo loại resource và trạng thái share.

**Sử dụng khi nào:**
- Hiển thị trang quản lý "Đã chia sẻ" — cho phép user xem lại và quản lý các share đang hoạt động.
- Kiểm tra share nào sắp hết hạn để gia hạn kịp thời.
- Lọc share đã bị revoke để biết lịch sử chia sẻ.

**Roles:** Tất cả.

**Query params:**

| Param | Type | Default | Mô tả |
|---|---|---|---|
| `resourceType` | `file` \| `folder` | null (all) | Lọc theo loại resource |
| `status` | `ACTIVE` \| `EXPIRED` \| `REVOKED` | null (all) | Lọc theo trạng thái |
| `page` | number | 1 | Trang hiện tại |
| `limit` | number | 10 | Số item mỗi trang (max 100) |

**Response:** `200`
```json
{
  "data": ["ShareWithResourceResponse"],
  "pagination": {
    "page": 1,
    "limit": 10,
    "total": 100,
    "totalPages": 10
  }
}
```

---

## 7. Lấy danh sách share với tôi

```
GET /api/v1/shares/with-me
```

**Chức năng:** Trả về danh sách tất cả resource đã được chia sẻ đến user hiện tại. Bao gồm 3 loại:
- Share trực tiếp với user (`sharedWithUserId = actorId`).
- Share với tenant mà user thuộc về (`sharedWithTenantId = actorTenantId`).
- Share với toàn bộ hệ thống (`allTenants = true`).

**Sử dụng khi nào:**
- Hiển thị trang "Được chia sẻ với tôi" — user xem các file/folder người khác đã share cho mình.
- Dùng để render danh sách tài nguyên có thể truy cập ngoài thư mục cá nhân.

**Roles:** Tất cả.

**Query params:**

| Param | Type | Default | Mô tả |
|---|---|---|---|
| `resourceType` | `file` \| `folder` | null (all) | Lọc theo loại resource |
| `page` | number | 1 | |
| `limit` | number | 10 | max 100 |

**Response:** `200`
```json
{
  "data": ["SharedWithMeResponse"],
  "pagination": { "page", "limit", "total", "totalPages" }
}
```

---

## 8. Thu hồi share (owner)

```
DELETE /api/v1/shares/{shareId}
```

**Chức năng:** Người tạo share tự thu hồi quyền truy cập của người nhận. Share chuyển sang trạng thái `REVOKED` và người nhận mất quyền truy cập ngay lập tức. Không thể gia hạn sau khi revoke — phải tạo share mới nếu muốn chia sẻ lại.

**Sử dụng khi nào:**
- User muốn dừng chia sẻ trước khi hết hạn (người nhận không còn cần truy cập).
- Rút lại quyền truy cập khẩn cấp khi file/folder chứa thông tin nhạy cảm.

**Roles:** Tất cả (chỉ người tạo share).

**Path param:** `shareId` (uuid)

**Response:** `204 No Content`

**Errors:**

| Code | HTTP | Mô tả |
|---|---|---|
| 1047 | 404 | Share không tồn tại |
| 1043 | 403 | Không phải người tạo share |

---

## 9. Admin thu hồi share

```
DELETE /api/v1/shares/admin/{shareId}
```

**Chức năng:** Admin thu hồi bất kỳ share nào trong phạm vi quyền quản lý mà không cần là người tạo share. `TENANT_ADMIN` chỉ thu hồi được share thuộc tenant của mình; `SUPER_ADMIN` có thể thu hồi bất kỳ share nào trong hệ thống.

**Sử dụng khi nào:**
- Admin cần xử lý khẩn cấp khi phát hiện share vi phạm chính sách (chia sẻ tài liệu sai đối tượng).
- Admin thu hồi share thay cho user đã bị khoá tài khoản hoặc nghỉ việc.
- Dọn dẹp share không hợp lệ mà người tạo không thể tự xử lý.

**Roles:** `TENANT_ADMIN`, `SUPER_ADMIN`.

**Path param:** `shareId` (uuid)

**Response:** `204 No Content`

**Errors:**

| Code | HTTP | Mô tả |
|---|---|---|
| 1047 | 404 | Share không tồn tại |
| 1049 | 403 | Không đủ quyền |

---

## 10. Admin bulk revoke share của một user

```
POST /api/v1/shares/admin/users/{userId}/revoke
```

**Chức năng:** Thu hồi hàng loạt tất cả share liên quan đến một user cụ thể. Có thể thu hồi theo 3 phạm vi: chỉ các share user đó đã **tạo ra** (`GRANTED`), chỉ các share user đó **nhận được** (`RECEIVED`), hoặc cả hai (`BOTH`). Trả về danh sách ID của các share đã bị thu hồi cùng audit ID để theo dõi.

**Sử dụng khi nào:**
- User nghỉ việc hoặc bị khoá tài khoản — cần thu hồi toàn bộ quyền truy cập liên quan đến họ ngay lập tức.
- User vi phạm chính sách bảo mật — cần thu hồi tất cả share họ đã tạo ra.
- Dọn dẹp dữ liệu khi offboard user khỏi hệ thống.

**Roles:** `TENANT_ADMIN`, `SUPER_ADMIN`.

**Path param:** `userId` (uuid)

**Request body:**
```json
{
  "scope": "GRANTED | RECEIVED | BOTH"
}
```

| Scope | Mô tả |
|---|---|
| `GRANTED` | Thu hồi tất cả share mà user đó đã tạo (người nhận mất quyền truy cập) |
| `RECEIVED` | Thu hồi tất cả share mà user đó đang nhận được (user mất quyền truy cập tài nguyên người khác share) |
| `BOTH` | Thu hồi cả hai chiều |

**Response:** `200`
```json
{
  "targetUserId": "uuid",
  "scope": "GRANTED | RECEIVED | BOTH",
  "revokedShareIds": ["uuid"],
  "revokedCount": 5,
  "auditId": "uuid"
}
```

**Errors:**

| Code | HTTP | Mô tả |
|---|---|---|
| 1004 | 404 | User không tồn tại |
| 1049 | 403 | Không đủ quyền |
