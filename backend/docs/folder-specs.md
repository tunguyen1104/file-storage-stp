## Folders

---

### POST /folders

**Description:** Create a new folder under a parent folder or at tenant root level.

**Auth:** Required — any authenticated user (`MEMBER`, `TENANT_ADMIN`, `SUPER_ADMIN`)

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
  "id": "folder-uuid",
  "name": "Project Documents",
  "parentId": null,
  "sizeBytes": 0,
  "tenantId": "tenant-uuid",
  "ownerId": null,
  "createdBy": "user-uuid",
  "createdAt": "2024-01-15T10:00:00Z",
  "updatedAt": "2024-01-15T10:00:00Z"
}
```

---

**Error Responses:**

| Status | Code | Condition |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | Missing or invalid fields |
| `401` | `UNAUTHORIZED` | Missing or invalid token |
| `403` | `FORBIDDEN` | No `EDIT` or `MANAGE` permission on parent folder |
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

**Auth:** Required — any authenticated user with access to the target folder

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
| `sort`      | string | ❌ | `name`, `createdAt`, or `sizeBytes` (default: `createdAt`) |
| `sortOrder` | string | ❌ | `asc` or `desc` (default: `desc`)             |
| `type`      | string | ❌ | `folder` or `file`          |
| `search`    | string | ❌ | Case-insensitive contains filter (optional)   |

---

**Success Response `200 OK`:**

```json
{
  "data": [
    {
      "id": "child-folder-uuid",
      "type": "folder",
      "name": "Design",
      "parentId": "folder-uuid",
      "sizeBytes": 0,
      "mimeType": null,
      "createdAt": "2024-01-15T10:00:00Z"
    },
    {
      "id": "file-uuid",
      "type": "file",
      "name": "wireframe.pdf",
      "parentId": "folder-uuid",
      "sizeBytes": 524288,
      "mimeType": "application/pdf",
      "createdAt": "2024-01-15T10:05:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "size": 10,
    "total": 2,
    "totalPages": 1
  }
}
```

---

**Error Responses:**

| Status | Code | Condition |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | Invalid `page`, `size`, or `sort` |
| `401` | `UNAUTHORIZED` | Missing or invalid token |
| `403` | `FORBIDDEN` | No access to target folder |
| `404` | `FOLDER_NOT_FOUND` | Folder does not exist or cross-tenant |

---

**Business Rules:**

- Route mapping: `GET /api/v1/folders/{folderId}/children`.
- Returns direct descendants only (depth = 1), includes both folders and files.
- For folders in the list, `sizeBytes` is always `0` to keep listing fast.
- Sorting always places folders before files.
- `search` filters only within the direct children of the target folder.
- Results are scoped to the caller's tenant and effective permissions.

---

### GET /folders/root

**Description:** List root-level items (folders and files) of the current tenant. Use this endpoint when there is no `folderId`.

**Auth:** Required — any authenticated user

---

**Query Params:**

| Field       | Type | Required | Description                                   |
|-------------| --- | --- |-----------------------------------------------|
| `page`      | integer | ❌ | Default: `1`                                  |
| `size`      | integer | ❌ | Default: `10`, max `100`                      |
| `sort`      | string | ❌ | `name`, `createdAt`, or `sizeBytes` (default: `createdAt`) |
| `sortOrder` | string | ❌ | `asc` or `desc` (default: `desc`)             |
| `type`      | string | ❌ | `folder` or `file`          |
| `search`    | string | ❌ | Case-insensitive contains filter (optional)   |

---

**Success Response `200 OK`:**

```json
{
  "data": [
    {
      "id": "root-folder-uuid",
      "type": "folder",
      "name": "Engineering",
      "parentId": null,
      "sizeBytes": 0,
      "mimeType": null,
      "createdAt": "2024-01-15T10:00:00Z"
    },
    {
      "id": "root-file-uuid",
      "type": "file",
      "name": "handbook.pdf",
      "parentId": null,
      "sizeBytes": 1048576,
      "mimeType": "application/pdf",
      "createdAt": "2024-01-15T10:05:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "size": 10,
    "total": 2,
    "totalPages": 1
  }
}
```

---

**Error Responses:**

| Status | Code | Condition |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | Invalid `page`, `size`, or `sort` |
| `401` | `UNAUTHORIZED` | Missing or invalid token |

---

**Business Rules:**

- Route mapping: `GET /api/v1/folders/root`.
- This endpoint is for the root case where no `folderId` is provided.
- Returns root-level items (depth = 1), includes both folders and files.
- For folders in the list, `sizeBytes` is always `0` to keep listing fast.
- Sorting always places folders before files.
- `search` filters only within root-level items.
- Results are scoped to the caller's tenant and effective permissions.

---

### GET /folders/:folderId/breadcrumbs

**Description:** Get breadcrumb chain from tenant root to the current folder.

**Auth:** Required — any authenticated user with access to the target folder

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
  ]
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

**Auth:** Required — any authenticated user with access to this folder

---

**Success Response `200 OK`:**

```json
{
  "id": "folder-uuid",
  "name": "Project Documents",
  "parentId": "parent-uuid",
  "sizeBytes": 0,
  "breadcrumb": [
    { "id": "root-uuid", "name": "root" },
    { "id": "design-uuid", "name": "Design" },
    { "id": "folder-uuid", "name": "Project Documents" }
  ],
  "ownerId": null,
  "tenantId": "tenant-uuid",
  "createdBy": "user-uuid",
  "createdAt": "2024-01-15T10:00:00Z",
  "updatedAt": "2024-01-15T10:00:00Z"
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

**Auth:** Required — any user with `MANAGE` permission on the folder

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
| `403` | `FORBIDDEN` | No `MANAGE` permission |
| `404` | `FOLDER_NOT_FOUND` | Does not exist or cross-tenant |
| `409` | `FOLDER_NAME_CONFLICT` | Name already used in same parent |

---

**Business Rules:**

- Rename applies to the target folder only.

---

### PATCH /folders/:folderId/move

**Description:** Move a folder to a different parent.

**Auth:** Required — any user with `MANAGE` permission on the folder

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
| `403` | `FORBIDDEN` | No `MANAGE` permission on the folder or target parent |
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

**Auth:** Required — any user with `MANAGE` permission on the folder

---

**Success Response `204 No Content`**

---

**Error Responses:**

| Status | Code | Condition |
| --- | --- | --- |
| `401` | `UNAUTHORIZED` | Missing or invalid token |
| `403` | `FORBIDDEN` | No `MANAGE` permission |
| `404` | `FOLDER_NOT_FOUND` | Does not exist or cross-tenant |
| `409` | `FOLDER_NOT_EMPTY` | Folder contains files or sub-folders |

---

**Business Rules:**

- Check is strict: any `ACTIVE` file or any child folder blocks deletion.
- Files with status `PENDING_DELETE` or `DELETED` inside the folder are ignored in emptiness check.