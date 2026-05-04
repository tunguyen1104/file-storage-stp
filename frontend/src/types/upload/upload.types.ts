// ─── API response shapes ──────────────────────────────────────────────────────

// POST /api/v1/uploads/presigned
// PresignedUploadInitResponse.java: { fileId: UUID, uploadUrl: String, expiresAt: Instant }
export type PresignedInitResponse = {
  fileId: string;     // UUID
  uploadUrl: string;  // PUT directly to MinIO
  expiresAt: string;  // ISO — TTL 15 minutes
};

// POST /api/v1/uploads/multipart/init
// MultipartInitResponse.java: { uploadId: String, fileId: UUID, expiresAt: Instant }
export type MultipartInitResponse = {
  uploadId: string;   // MinIO upload ID (string, not UUID)
  fileId: string;     // UUID
  expiresAt: string;  // ISO — session TTL 24h
};

// GET /api/v1/uploads/multipart/{uploadId}/part-url
// PartUrlResponse.java: { uploadId, partNumber, partUrl, expiresAt }
export type PartUrlResponse = {
  uploadId: string;
  partNumber: number;
  partUrl: string;    // presigned PUT URL for 1 chunk, TTL 30 minutes
  expiresAt: string;  // ISO
};

// POST /api/v1/uploads/multipart/{uploadId}/parts/{n}/complete — request body
// CompletePartRequest.java: { etag: String }
export type CompletePartPayload = {
  etag: string;       // ETag from PUT chunk response header
};

// POST /api/v1/uploads/multipart/{uploadId}/complete — request body
// CompleteMultipartRequest.java: { parts: [{partNumber, etag}] }
export type CompleteMultipartPayload = {
  parts: { partNumber: number; etag: string }[];
};

// GET /api/v1/uploads/multipart/{uploadId}
// MultipartSessionResponse.java: { uploadId, fileId, status, totalParts, uploadedParts, expiresAt }
// ⚠️ NO "fileName" field — backend does not return file name in session response
export type MultipartSessionResponse = {
  uploadId: string;
  fileId: string;     // UUID
  status: "IN_PROGRESS" | "COMPLETED" | "ABORTED";
  totalParts: number;
  uploadedParts: { partNumber: number; etag: string }[];
  expiresAt: string;  // ISO
};

// GET /api/v1/files/{fileId}/status
// FileStatusResponse.java: { fileId: UUID, status: FileStatus, originalName: String, updatedAt: Instant }
export type FileStatusResponse = {
  fileId: string;     // UUID — ⚠️ field name is `fileId`, not `id`
  status: "PENDING" | "SCANNING" | "ACTIVE" | "INFECTED" | "UPLOAD_FAILED";
  originalName: string;
  updatedAt: string;  // ISO
};

// ─── Internal UI state ────────────────────────────────────────────────────────

export type UploadStatus =
  | "queued"
  | "uploading"
  | "confirming"
  | "scanning"
  | "done"
  | "error"
  | "cancelled"
  | "infected";

export type PartInfo = {
  partNumber: number;
  etag: string;
};

export interface UploadItem {
  id: string;                   // client-side uuid
  file: File;
  folderId?: string;

  status: UploadStatus;
  progress: number;             // 0-100
  speedBps?: number;            // bytes/sec
  etaSeconds?: number;

  // presigned
  fileId?: string;
  uploadUrl?: string;
  presignedExpiresAt?: string;  // ISO — from PresignedInitResponse.expiresAt

  // multipart
  uploadId?: string;
  totalParts?: number;
  uploadedParts: PartInfo[];
  sessionExpiresAt?: string;    // ISO — from MultipartInitResponse.expiresAt

  errorMessage?: string;
  abortController?: AbortController;
}

/**
 * Persisted to localStorage to allow resume of multipart upload after browser refresh.
 * File bytes cannot be serialized — only metadata is stored to identify + resume.
 *
 * Dedup key is `uploadId` (MinIO session ID, globally unique) — NOT fileName.
 * → No conflict even when multiple files with the same name are uploaded in parallel.
 * → Each record maps 1-1 with one MinIO multipart session.
 *
 * Edge case: user uploads the same file twice, both interrupted by refresh
 *   → localStorage has 2 records with same fileName + fileSizeBytes, different uploadId
 *   → Shows 2 separate InterruptedUploadBanners — no data corruption
 *      because resume uses uploadId, not fileName, for lookup.
 */
export interface PendingMultipartRecord {
  itemId: string;         // client-side uuid (matches UploadItem.id)
  uploadId: string;       // MinIO session ID — used to call GET /uploads/multipart/{uploadId}
  fileId: string;         // backend file ID (UUID)
  fileName: string;       // to display name for user during resume (not in SessionResponse!)
  fileSizeBytes: number;  // to validate when user re-selects file
  mimeType: string;
  folderId?: string;
  startedAt: string;      // ISO — filter out records > 24h (backend session expired)
}
