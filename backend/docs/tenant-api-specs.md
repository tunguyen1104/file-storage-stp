# Tenant API Specs

Base path: `/api/v1/tenants`  
Authentication: Bearer token required on all endpoints.

---

## Common Response Shape

### TenantResponse

Trả về ở hầu hết các API. `userCount` chỉ có giá trị ở các API trả về danh sách hoặc detail, null ở một số context.

```json
{
  "id": "uuid",
  "name": "string",
  "slug": "string",
  "storageQuotaBytes": "number",
  "storageUsedBytes": "number",
  "isActive": "boolean",
  "userCount": "number | null",
  "createdBy": "uuid",
  "createdAt": "ISO-8601",
  "updatedAt": "ISO-8601"
}
```

### TenantOptionResponse

Dùng riêng ở API `GET /options`, chỉ trả về 2 trường:

```json
{
  "id": "uuid",
  "name": "string"
}
```

---

## 1. Tạo Tenant

```
POST /api/v1/tenants
```

**Chức năng:** Tạo một tenant mới trong hệ thống. Tenant được tạo ra ở trạng thái `active = true` ngay lập tức. `slug` được tự động lowercase trước khi lưu.

**Sử dụng khi nào:** SUPER_ADMIN onboard một tổ chức/khách hàng mới vào hệ thống.

**Roles:** `SUPER_ADMIN`.

**Request body:**

| Field | Type | Bắt buộc | Ràng buộc | Mô tả |
|---|---|---|---|---|
| `name` | string | Có | max 255 ký tự | Tên hiển thị của tổ chức |
| `slug` | string | Có | max 100 ký tự, chỉ `a-z`, `0-9`, `-` | Định danh duy nhất dạng URL-friendly |
| `storageQuotaBytes` | number | Không | > 0 | Dung lượng lưu trữ cấp phát (bytes). Mặc định: **50 GB** (53,687,091,200 bytes) nếu không truyền |

```json
{
  "name": "Công ty ABC",
  "slug": "cong-ty-abc",
  "storageQuotaBytes": 107374182400
}
```

**Rules:**
- `slug` phải unique toàn hệ thống.
- `slug` được normalize về lowercase trước khi kiểm tra trùng.
- `storageUsedBytes` khởi tạo bằng `0`.

**Response:** `201 TenantResponse`

**Errors:**

| Code | HTTP | Mô tả |
|---|---|---|
| 3 | 403 | Không phải SUPER_ADMIN |
| 1 | 400 | name hoặc slug null/blank; storageQuotaBytes ≤ 0 |
| 1019 | 409 | slug đã tồn tại |

---

## 2. Lấy danh sách Tenant

```
GET /api/v1/tenants
```

**Chức năng:** Trả về danh sách phân trang tất cả tenant trong hệ thống, kèm `userCount` (số lượng user của từng tenant). Có thể lọc theo trạng thái active và tìm kiếm theo tên hoặc slug. Kết quả sắp xếp theo `createdAt` giảm dần (mới nhất trước).

**Sử dụng khi nào:**
- SUPER_ADMIN xem tổng quan tất cả tổ chức đang hoạt động trong hệ thống.
- Tìm kiếm một tenant cụ thể để thực hiện quản trị (cập nhật quota, deactivate, ...).
- Lọc riêng danh sách tenant đang bị deactivate để review.

**Roles:** `SUPER_ADMIN`.

**Query params:**

| Param | Type | Default | Mô tả |
|---|---|---|---|
| `isActive` | boolean | null (all) | `true` → chỉ lấy tenant đang active; `false` → chỉ lấy tenant bị deactivate |
| `search` | string | null | Tìm kiếm theo `name` hoặc `slug` (contains, case-insensitive) |
| `page` | number | 1 | Trang hiện tại (1-based) |
| `limit` | number | 10 | Số item mỗi trang (min 1, max 100) |

**Response:** `200`

```json
{
  "data": [
    {
      "id": "uuid",
      "name": "Công ty ABC",
      "slug": "cong-ty-abc",
      "storageQuotaBytes": 107374182400,
      "storageUsedBytes": 5368709120,
      "isActive": true,
      "userCount": 42,
      "createdBy": "uuid",
      "createdAt": "2025-01-10T08:00:00Z",
      "updatedAt": "2025-06-01T12:00:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 10,
    "total": 25,
    "totalPages": 3
  }
}
```

**Errors:**

| Code | HTTP | Mô tả |
|---|---|---|
| 3 | 403 | Không phải SUPER_ADMIN |

---

## 3. Lấy thông tin Tenant của mình

```
GET /api/v1/tenants/me
```

**Chức năng:** Trả về thông tin tenant mà người dùng hiện tại thuộc về, kèm `userCount` và `storageUsedBytes`. Dùng để hiển thị thông tin và mức sử dụng tài nguyên của tổ chức trên trang cài đặt.

**Sử dụng khi nào:**
- TENANT_ADMIN xem thông tin tổ chức mình (quota đã dùng, còn lại, số lượng thành viên) trên dashboard hoặc trang Settings.
- SUPER_ADMIN xem thông tin tenant của chính mình (nếu SUPER_ADMIN thuộc một tenant).

**Roles:** `SUPER_ADMIN`, `TENANT_ADMIN`.

**Request body:** Không có.

**Response:** `200 TenantResponse`

```json
{
  "id": "uuid",
  "name": "Công ty ABC",
  "slug": "cong-ty-abc",
  "storageQuotaBytes": 107374182400,
  "storageUsedBytes": 5368709120,
  "isActive": true,
  "userCount": 42,
  "createdBy": "uuid",
  "createdAt": "2025-01-10T08:00:00Z",
  "updatedAt": "2025-06-01T12:00:00Z"
}
```

**Errors:**

| Code | HTTP | Mô tả |
|---|---|---|
| 2 | 401 | Chưa xác thực |
| 3 | 403 | Role không được phép (MEMBER) |
| 1014 | 404 | Tenant của user không tồn tại |

---

## 4. Lấy thông tin Tenant theo ID

```
GET /api/v1/tenants/{tenantId}
```

**Chức năng:** Trả về thông tin chi tiết của bất kỳ tenant nào theo ID, kèm `userCount`. TENANT_ADMIN **không** có quyền dùng endpoint này (phải dùng `GET /me`).

**Sử dụng khi nào:**
- SUPER_ADMIN xem chi tiết một tenant cụ thể trước khi thực hiện hành động quản trị (cập nhật quota, deactivate, ...).
- Tra cứu thông tin tenant sau khi chọn từ danh sách.

**Roles:** `SUPER_ADMIN`.

**Path param:** `tenantId` (uuid)

**Response:** `200 TenantResponse`

**Errors:**

| Code | HTTP | Mô tả |
|---|---|---|
| 3 | 403 | Không phải SUPER_ADMIN |
| 1014 | 404 | Tenant không tồn tại |

---

## 5. Cập nhật Tenant

```
PATCH /api/v1/tenants/{tenantId}
```

**Chức năng:** Cập nhật thông tin của tenant: tên hiển thị, slug và storage quota. Tất cả 3 field đều bắt buộc trong request. Slug được normalize về lowercase trước khi kiểm tra trùng. Không thay đổi trạng thái `isActive` — dùng API Activate/Deactivate riêng.

**Sử dụng khi nào:**
- Tăng/giảm storage quota khi tổ chức mở rộng hoặc thu hẹp hợp đồng.
- Đổi tên hiển thị hoặc slug theo yêu cầu của khách hàng.

**Roles:** `SUPER_ADMIN`.

**Path param:** `tenantId` (uuid)

**Request body:**

| Field | Type | Bắt buộc | Ràng buộc | Mô tả |
|---|---|---|---|---|
| `name` | string | Có | max 255 ký tự | Tên mới của tổ chức |
| `slug` | string | Có | max 100 ký tự, chỉ `a-z`, `0-9`, `-` | Slug mới |
| `storageQuotaBytes` | number | Không | > 0, ≥ storageUsedBytes hiện tại | Quota mới. Không truyền thì giữ nguyên giá trị cũ |

```json
{
  "name": "Công ty ABC (Mới)",
  "slug": "cong-ty-abc-moi",
  "storageQuotaBytes": 214748364800
}
```

**Rules:**
- `slug` phải unique, không trùng với tenant khác (tự slug của mình thì được).
- `storageQuotaBytes` không được nhỏ hơn `storageUsedBytes` hiện tại — không thể thu hẹp quota về dưới mức đang dùng.

**Response:** `200 TenantResponse`

**Errors:**

| Code | HTTP | Mô tả |
|---|---|---|
| 3 | 403 | Không phải SUPER_ADMIN |
| 1 | 400 | name hoặc slug null/blank; storageQuotaBytes ≤ 0 |
| 1014 | 404 | Tenant không tồn tại |
| 1019 | 409 | slug đã được dùng bởi tenant khác |
| 1015 | 400 | storageQuotaBytes nhỏ hơn dung lượng đang sử dụng |

---

## 6. Deactivate Tenant

```
DELETE /api/v1/tenants/{tenantId}
```

**Chức năng:** Vô hiệu hoá một tenant (`isActive = false`). Toàn bộ user thuộc tenant đó mất quyền đăng nhập ngay lập tức. Dữ liệu (file, folder, share) vẫn được giữ nguyên, không xoá vĩnh viễn. Nếu tenant đã ở trạng thái inactive thì không làm gì (idempotent). Tenant có slug `"system"` không thể bị deactivate.

**Sử dụng khi nào:**
- Tổ chức hết hạn hợp đồng hoặc vi phạm điều khoản — cần khoá truy cập ngay.
- Tạm ngưng tenant để điều tra sự cố bảo mật, sau đó activate lại.
- Offboard tổ chức ra khỏi hệ thống nhưng vẫn cần giữ dữ liệu (không muốn xoá hẳn).

**Roles:** `SUPER_ADMIN`.

**Path param:** `tenantId` (uuid)

**Request body:** Không có.

**Response:** `200 TenantResponse` (với `isActive = false`)

**Errors:**

| Code | HTTP | Mô tả |
|---|---|---|
| 3 | 403 | Không phải SUPER_ADMIN |
| 1014 | 404 | Tenant không tồn tại |
| 1020 | 409 | Không thể deactivate system tenant (slug = "system") |

---

## 7. Activate Tenant

```
PATCH /api/v1/tenants/{tenantId}/activate
```

**Chức năng:** Kích hoạt lại một tenant đang bị vô hiệu hoá (`isActive = false → true`). Ngay sau khi activate, toàn bộ user thuộc tenant đó có thể đăng nhập và sử dụng hệ thống trở lại. Nếu tenant đã ở trạng thái `active = true` thì không làm gì (idempotent — không trả lỗi).

Dùng pessimistic lock (`SELECT ... FOR UPDATE`) khi truy vấn tenant để tránh race condition nếu nhiều request activate đồng thời.

**Sử dụng khi nào:**
- Tái kích hoạt tổ chức sau khi họ gia hạn hợp đồng hoặc hoàn tất xử lý vi phạm.
- Bật lại tenant sau khi quá trình điều tra bảo mật kết thúc.
- Khôi phục khi deactivate nhầm.

**Roles:** `SUPER_ADMIN`.

**Path param:** `tenantId` (uuid)

**Request body:** Không có.

**Response:** `200 TenantResponse`

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "name": "Công ty ABC",
  "slug": "cong-ty-abc",
  "storageQuotaBytes": 107374182400,
  "storageUsedBytes": 5368709120,
  "isActive": true,
  "userCount": null,
  "createdBy": "uuid",
  "createdAt": "2025-01-10T08:00:00Z",
  "updatedAt": "2026-05-17T15:30:00Z"
}
```

> `userCount = null` ở response này vì service không query user count khi activate.

**Errors:**

| Code | HTTP | Mô tả |
|---|---|---|
| 3 | 403 | Không phải SUPER_ADMIN |
| 1014 | 404 | Tenant không tồn tại |

---

## 8. Lấy danh sách Tenant dạng option (dropdown)

```
GET /api/v1/tenants/options
```

**Chức năng:** Trả về danh sách rút gọn của tất cả tenant đang **active**, chỉ gồm `id` và `name`. Không phân trang. Kết quả sắp xếp theo `name` tăng dần (A → Z). Tenant bị deactivate không xuất hiện trong danh sách này.

**Sử dụng khi nào:**
- Populate dropdown chọn tenant khi SUPER_ADMIN muốn share file/folder với một tenant cụ thể (`POST /api/v1/shares/files/tenant` với `tenantId`).
- Hiển thị danh sách tenant để SUPER_ADMIN chọn khi thực hiện các thao tác quản trị khác.

**Roles:** `SUPER_ADMIN`.

**Request body:** Không có.

**Response:** `200 List<TenantOptionResponse>`

```json
[
  { "id": "uuid-1", "name": "Công ty ABC" },
  { "id": "uuid-2", "name": "Công ty XYZ" }
]
```

**Errors:**

| Code | HTTP | Mô tả |
|---|---|---|
| 3 | 403 | Không phải SUPER_ADMIN |

---

## Tổng hợp

| # | Method | Path | Role | Mô tả ngắn |
|---|---|---|---|---|
| 1 | POST | `/` | SUPER_ADMIN | Tạo tenant mới |
| 2 | GET | `/` | SUPER_ADMIN | Danh sách tenant (phân trang, lọc) |
| 3 | GET | `/me` | SUPER_ADMIN, TENANT_ADMIN | Thông tin tenant của mình |
| 4 | GET | `/{tenantId}` | SUPER_ADMIN | Thông tin tenant theo ID |
| 5 | PATCH | `/{tenantId}` | SUPER_ADMIN | Cập nhật tên, slug, quota |
| 6 | DELETE | `/{tenantId}` | SUPER_ADMIN | Deactivate tenant |
| 7 | PATCH | `/{tenantId}/activate` | SUPER_ADMIN | Activate lại tenant |
| 8 | GET | `/options` | SUPER_ADMIN | Danh sách tenant active dạng dropdown |
