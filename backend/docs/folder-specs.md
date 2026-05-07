## Folders

---

### POST /folders

**Description:** Create a new folder under a parent folder or at tenant root level.

**Auth:** Required — authenticated `MEMBER`, `TENANT_ADMIN`, or `SUPER_ADMIN`. If `parentId` is provided, caller must be allowed to modify that parent (`MEMBER`: only folders created by self in the same tenant; `TENANT_ADMIN`: any folder in the same tenant; `SUPER_ADMIN`: any folder).

---

**Request Body:**

```json
{
  "name": "Project Documents",
  "parentId": null
}
```

| Field | Type | Required | Constraints |
| --- | --- | --- | --- |
| `name` | string | ✅ | Max 255 chars. Must be unique within the same parent |
| `parentId` | UUID or `null` | ❌ | If provided, must belong to the caller's tenant. `null` means create at tenant root level |

---

**Success Response `201 Created`:**

```json
{
  "data": {
    "id": "folder-uuid",
    "name": "Project Documents",
    "parentId": null,
    "sizeBytes": 0,
    "tenantId": "tenant-uuid",
    "ownerId": "user-uuid",
    "createdBy": "user-uuid",
    "createdAt": "2024-01-15T10:00:00Z",
    "updatedAt": "2024-01-15T10:00:00Z",
    "breadcrumb": null
  },
  "success": true
}
```

---

**Error Responses:**

| Status | Code | Condition |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | Missing or invalid fields |
| `401` | `UNAUTHORIZED` | Missing or invalid token |
| `403` | `FORBIDDEN` | Caller cannot modify the target parent folder |
| `404` | `FOLDER_NOT_FOUND` | `parentId` does not exist or cross-tenant |
| `409` | `FOLDER_NAME_CONFLICT` | Folder name already exists in same parent |

---

**Business Rules:**

- `tenant_id` is derived from the authenticated user's tenant, not from the request body.
- If `parentId = null`, the folder is created as a root-level folder under the tenant root.
- `created_by` is set to the caller's user ID.

---

### GET /folders/{folderId}/children

**Description:** List direct children of a folder, including both folders and files.

**Auth:** Required — authenticated user with read access to the target folder (`MEMBER`: only folders created by self in the same tenant; `TENANT_ADMIN`: any folder in the same tenant; `SUPER_ADMIN`: any folder).

---

**Path Params:**

| Field | Type | Description |
| --- | --- | --- |
| `folderId` | UUID | Parent folder ID |

---

**Query Params:**

| Field       | Type | Required | Description                                   |
|-------------| --- | --- |-----------------------------------------------|
| `page`      | integer | ❌ | Default: `1`                                  |
| `size`      | integer | ❌ | Default: `10`, max `100`                      |
| `sort`      | string | ❌ | Exact enum: `NAME`, `CREATED_AT`, or `SIZE_BYTES` (default: `CREATED_AT`) |
| `sortOrder` | string | ❌ | Exact enum: `ASC` or `DESC`. Default: `DESC` when `sort = CREATED_AT`, otherwise `ASC` |
| `type`      | string | ❌ | Exact enum: `FOLDER` or `FILE` |
| `search`    | string | ❌ | Case-insensitive contains filter (optional)   |

---

**Success Response `200 OK`:**

```json
{
  "data": {
    "data": [
      {
        "id": "child-folder-uuid",
        "type": "folder",
        "name": "Design",
        "parentId": "folder-uuid",
        "sizeBytes": 0,
        "mimeType": null,
        "status": null,
        "actor": {
          "id": "user-uuid",
          "fullName": "Jane Doe"
        },
        "createdAt": "2024-01-15T10:00:00Z",
        "updatedAt": "2024-01-15T10:00:00Z"
      },
      {
        "id": "file-uuid",
        "type": "file",
        "name": "wireframe.pdf",
        "parentId": "folder-uuid",
        "sizeBytes": 524288,
        "mimeType": "application/pdf",
        "status": "ACTIVE",
        "actor": {
          "id": "user-uuid",
          "fullName": "Jane Doe"
        },
        "createdAt": "2024-01-15T10:05:00Z",
        "updatedAt": "2024-01-15T10:05:00Z"
      }
    ],
    "pageSize": 10,
    "totalPages": 1,
    "totalElements": 2,
    "currentPage": 1
  },
  "success": true
}
```

---

**Error Responses:**

| Status | Code | Condition |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | Invalid `page`, `size`, `sort`, or `sortOrder` |
| `401` | `UNAUTHORIZED` | Missing or invalid token |
| `403` | `FORBIDDEN` | No access to target folder |
| `404` | `FOLDER_NOT_FOUND` | Folder does not exist or cross-tenant |

---

**Business Rules:**

- Route mapping: `GET /api/v1/folders/{folderId}/children`.
- Returns direct descendants only (depth = 1), includes both folders and files.
- For folders in the list, `sizeBytes` is always `0` to keep listing fast.
- Sorting always places folders before files.
- Request enums for sorting are uppercase: `sort = NAME | CREATED_AT | SIZE_BYTES`, `sortOrder = ASC | DESC`.
- Request enum `type` is uppercase (`FOLDER` or `FILE`), while response field `type` is lowercase (`folder` or `file`).
- `search` filters only within the direct children of the target folder.
- Results are scoped to the caller's tenant and effective permissions.

---

### GET /folders/root

**Description:** List root-level items (folders and files) of the current tenant. Use this endpoint when there is no `folderId`.

**Auth:** Required — authenticated `MEMBER`, `TENANT_ADMIN`, or `SUPER_ADMIN`. `MEMBER` sees only root-level items created/uploaded by self; `TENANT_ADMIN` and `SUPER_ADMIN` can list all root-level items in the current tenant.

---

**Query Params:**

| Field       | Type | Required | Description                                   |
|-------------| --- | --- |-----------------------------------------------|
| `page`      | integer | ❌ | Default: `1`                                  |
| `size`      | integer | ❌ | Default: `10`, max `100`                      |
| `sort`      | string | ❌ | Exact enum: `NAME`, `CREATED_AT`, or `SIZE_BYTES` (default: `CREATED_AT`) |
| `sortOrder` | string | ❌ | Exact enum: `ASC` or `DESC`. Default: `DESC` when `sort = CREATED_AT`, otherwise `ASC` |
| `type`      | string | ❌ | Exact enum: `FOLDER` or `FILE` |
| `search`    | string | ❌ | Case-insensitive contains filter (optional)   |

---

**Success Response `200 OK`:**

```json
{
  "data": {
    "data": [
      {
        "id": "root-folder-uuid",
        "type": "folder",
        "name": "Engineering",
        "parentId": null,
        "sizeBytes": 0,
        "mimeType": null,
        "status": null,
        "actor": {
          "id": "user-uuid",
          "fullName": "Jane Doe"
        },
        "createdAt": "2024-01-15T10:00:00Z",
        "updatedAt": "2024-01-15T10:00:00Z"
      },
      {
        "id": "root-file-uuid",
        "type": "file",
        "name": "handbook.pdf",
        "parentId": null,
        "sizeBytes": 1048576,
        "mimeType": "application/pdf",
        "status": "ACTIVE",
        "actor": {
          "id": "user-uuid",
          "fullName": "Jane Doe"
        },
        "createdAt": "2024-01-15T10:05:00Z",
        "updatedAt": "2024-01-15T10:05:00Z"
      }
    ],
    "pageSize": 10,
    "totalPages": 1,
    "totalElements": 2,
    "currentPage": 1
  },
  "success": true
}
```

---

**Error Responses:**

| Status | Code | Condition |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | Invalid `page`, `size`, `sort`, or `sortOrder` |
| `401` | `UNAUTHORIZED` | Missing or invalid token |

---

**Business Rules:**

- Route mapping: `GET /api/v1/folders/root`.
- This endpoint is for the root case where no `folderId` is provided.
- Returns root-level items (depth = 1), includes both folders and files.
- For folders in the list, `sizeBytes` is always `0` to keep listing fast.
- Sorting always places folders before files.
- Request enums for sorting are uppercase: `sort = NAME | CREATED_AT | SIZE_BYTES`, `sortOrder = ASC | DESC`.
- Request enum `type` is uppercase (`FOLDER` or `FILE`), while response field `type` is lowercase (`folder` or `file`).
- `search` filters only within root-level items.
- Results are scoped to the caller's tenant and effective permissions.

---

### GET /folders/admin/items

**Description:** List folders and files across tenants for one hierarchy level at a time. This endpoint is intended for `SUPER_ADMIN` to browse either root-level items or the direct children of a specific parent folder.

**Auth:** Required — `SUPER_ADMIN` only

---

**Query Params:**

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `tenantId` | UUID | ❌ | Filter by tenant. If omitted, returns items from all tenants |
| `type` | string | ❌ | Exact enum: `FOLDER` or `FILE`. If omitted, returns both |
| `parentId` | UUID | ❌ | If omitted, returns root-level items only. If provided, returns direct children of that folder only |
| `status` | string | ❌ | Exact enum: `PENDING`, `SCANNING`, `ACTIVE`, `PENDING_DELETE`, `INFECTED`, `UPLOAD_FAILED`, `DELETED`. Applies to files only |
| `createdBy` | UUID | ❌ | For folders filters `created_by`; for files filters `uploaded_by` |
| `search` | string | ❌ | Case-insensitive contains filter on folder/file name, using current Vietnamese unaccent behavior |
| `page` | integer | ❌ | Default: `1`, minimum effective value: `1` |
| `limit` | integer | ❌ | Default: `10`, minimum effective value: `1`, maximum effective value: `100` |
| `sortBy` | string | ❌ | Exact enum: `NAME`, `CREATED_AT`, `SIZE_BYTES`, `TENANT_NAME` (default: `CREATED_AT`) |
| `sortDirection` | string | ❌ | Exact enum: `ASC` or `DESC`. Default: `DESC` when `sortBy = CREATED_AT`, otherwise `ASC` |

---

**Success Response `200 OK`:**

```json
{
  "data": {
    "data": [
      {
        "id": "folder-uuid",
        "type": "folder",
        "name": "Engineering",
        "tenantId": "tenant-uuid",
        "tenantName": "Acme Corp",
        "parentId": null,
        "parentName": null,
        "sizeBytes": 0,
        "mimeType": null,
        "status": null,
        "actor": {
          "id": "user-uuid",
          "fullName": "Jane Doe"
        },
        "createdAt": "2024-01-15T10:00:00Z",
        "updatedAt": "2024-01-15T10:00:00Z"
      },
      {
        "id": "file-uuid",
        "type": "file",
        "name": "handbook.pdf",
        "tenantId": "tenant-uuid",
        "tenantName": "Acme Corp",
        "parentId": null,
        "parentName": null,
        "sizeBytes": 1048576,
        "mimeType": "application/pdf",
        "status": "ACTIVE",
        "actor": {
          "id": "user-uuid",
          "fullName": "Jane Doe"
        },
        "createdAt": "2024-01-15T10:05:00Z",
        "updatedAt": "2024-01-15T10:05:00Z"
      }
    ],
    "currentPage": 1,
    "pageSize": 10,
    "totalElements": 2,
    "totalPages": 1
  },
  "success": true
}
```

---

**Error Responses:**

| Status | Code | Condition |
| --- | --- | --- |
| `400` | `INVALID_PAYLOAD` | Invalid enum/value in query params, or `type = FOLDER` used together with `status` |
| `401` | `UNAUTHORIZED` | Missing or invalid token |
| `403` | `FORBIDDEN` | Caller is authenticated but is not `SUPER_ADMIN` |

---

**Business Rules:**

- Route mapping: `GET /api/v1/folders/admin/items`.
- Returns one hierarchy level at a time: root-level items when `parentId` is omitted, or direct children only when `parentId` is provided.
- If `status` is provided while `type` is omitted, the endpoint returns file rows only.
- If `type = FOLDER` and `status` is provided, the request is rejected with `INVALID_PAYLOAD`.
- Folders always appear before files, regardless of `sortBy` or `sortDirection`.
- Sorting is applied within each type group, with a stable secondary sort by `id ASC`.
- Response `type` is lowercased as `folder` or `file`, even though request enums are uppercase.
- For folder rows, `sizeBytes` is always `0`, `mimeType` is `null`, and `status` is `null`.
- `search` matches folder `name` and file `originalName` using the same Vietnamese unaccent search behavior as the existing folder listing APIs.
- Unknown or cross-tenant `tenantId` / `parentId` are not validated as `404`; the endpoint may simply return an empty result.

---

### GET /folders/:folderId/breadcrumbs

**Description:** Get breadcrumb chain from tenant root to the current folder.

**Auth:** Required — authenticated user with read access to the target folder (`MEMBER`: only folders created by self in the same tenant; `TENANT_ADMIN`: any folder in the same tenant; `SUPER_ADMIN`: any folder).

---

**Path Params:**

| Field | Type | Description |
| --- | --- | --- |
| `folderId` | UUID | Target folder ID |

---

**Success Response `200 OK`:**

```json
{
  "data": [
    { "id": "root-uuid", "name": "root" },
    { "id": "design-uuid", "name": "Design" },
    { "id": "folder-uuid", "name": "Project Documents" }
  ],
  "success": true
}
```

---

**Error Responses:**

| Status | Code | Condition |
| --- | --- | --- |
| `401` | `UNAUTHORIZED` | Missing or invalid token |
| `403` | `FORBIDDEN` | No access to target folder |
| `404` | `FOLDER_NOT_FOUND` | Folder does not exist or cross-tenant |

---

**Business Rules:**

- Route mapping: `GET /api/v1/folders/{folderId}/breadcrumbs`.
- Breadcrumb is resolved using a `WITH RECURSIVE` CTE traversing `parent_id`.
- Response is ordered from root to current folder.

---

### GET /folders/:folderId

**Description:** Get folder details including the breadcrumb from root.

**Auth:** Required — authenticated user with read access to the target folder (`MEMBER`: only folders created by self in the same tenant; `TENANT_ADMIN`: any folder in the same tenant; `SUPER_ADMIN`: any folder).

---

**Success Response `200 OK`:**

```json
{
  "data": {
    "id": "folder-uuid",
    "name": "Project Documents",
    "parentId": "parent-uuid",
    "sizeBytes": 0,
    "tenantId": "tenant-uuid",
    "ownerId": "user-uuid",
    "createdBy": "user-uuid",
    "createdAt": "2024-01-15T10:00:00Z",
    "updatedAt": "2024-01-15T10:00:00Z",
    "breadcrumb": [
      { "id": "root-uuid", "name": "root" },
      { "id": "design-uuid", "name": "Design" },
      { "id": "folder-uuid", "name": "Project Documents" }
    ]
  },
  "success": true
}
```

---

**Error Responses:**

| Status | Code | Condition |
| --- | --- | --- |
| `401` | `UNAUTHORIZED` | Missing or invalid token |
| `403` | `FORBIDDEN` | No access to this folder |
| `404` | `FOLDER_NOT_FOUND` | Does not exist or cross-tenant |

---

**Business Rules:**

- Breadcrumb is built using a `WITH RECURSIVE` CTE traversing `parent_id`.
- `sizeBytes` is computed using a recursive CTE over the folder subtree.

---

### PATCH /folders/:folderId

**Description:** Rename a folder.

**Auth:** Required — authenticated user with modify access to the target folder (`MEMBER`: only folders created by self in the same tenant; `TENANT_ADMIN`: any folder in the same tenant; `SUPER_ADMIN`: any folder).

---

**Request Body:**

```json
{
  "name": "New Folder Name"
}
```

| Field | Type | Required | Constraints |
| --- | --- | --- | --- |
| `name` | string | ✅ | Max 255 chars. Must be unique within same parent |

---

**Success Response `200 OK`:** *(updated folder object, same shape as POST /folders)*

---

**Error Responses:**

| Status | Code | Condition |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | Missing or invalid `name` |
| `401` | `UNAUTHORIZED` | Missing or invalid token |
| `403` | `FORBIDDEN` | Caller cannot modify the target folder |
| `404` | `FOLDER_NOT_FOUND` | Does not exist or cross-tenant |
| `409` | `FOLDER_NAME_CONFLICT` | Name already used in same parent |

---

**Business Rules:**

- Rename applies to the target folder only.

---

### PATCH /folders/:folderId/move

**Description:** Move a folder to a different parent.

**Auth:** Required — authenticated user with modify access to the target folder. If `parentId` is provided, caller must also be allowed to modify the target parent (`MEMBER`: only folders created by self in the same tenant; `TENANT_ADMIN`: any folder in the same tenant; `SUPER_ADMIN`: any folder).

---

**Request Body:**

```json
{
  "parentId": "new-parent-uuid"
}
```

| Field | Type | Required | Constraints |
| --- | --- | --- | --- |
| `parentId` | UUID or `null` | ✅ | Must belong to caller's tenant. `null` moves to tenant root |

---

**Success Response `200 OK`:** *(updated folder object, same shape as POST /folders)*

---

**Error Responses:**

| Status | Code | Condition |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | Missing or invalid `parentId` |
| `401` | `UNAUTHORIZED` | Missing or invalid token |
| `403` | `FORBIDDEN` | Caller cannot modify the source folder or the target parent folder |
| `404` | `FOLDER_NOT_FOUND` | Target parent does not exist or cross-tenant |
| `409` | `FOLDER_NAME_CONFLICT` | Name already used in target parent |
| `409` | `FOLDER_INVALID_MOVE` | Cannot move into itself or its descendants |

---

**Business Rules:**

- `parentId = null` moves the folder to tenant root.
- Folder must remain within the same tenant.
- Moving updates ancestry and affects permissions inherited from the new parent.
- Validation for `FOLDER_INVALID_MOVE` uses a recursive CTE to ensure `parentId` is not the folder itself or any of its descendants.

---

### DELETE /folders/:folderId

**Description:** Delete a folder. The folder must be empty (no files, no sub-folders).

**Auth:** Required — authenticated user with modify access to the target folder (`MEMBER`: only folders created by self in the same tenant; `TENANT_ADMIN`: any folder in the same tenant; `SUPER_ADMIN`: any folder).

---

**Success Response `204 No Content`**

---

**Error Responses:**

| Status | Code | Condition |
| --- | --- | --- |
| `401` | `UNAUTHORIZED` | Missing or invalid token |
| `403` | `FORBIDDEN` | Caller cannot modify the target folder |
| `404` | `FOLDER_NOT_FOUND` | Does not exist or cross-tenant |
| `409` | `FOLDER_NOT_EMPTY` | Folder contains files or sub-folders |

---

**Business Rules:**

- Check is strict: any `ACTIVE` file or any child folder blocks deletion.
- Files with status `PENDING_DELETE` or `DELETED` inside the folder are ignored in emptiness check.
