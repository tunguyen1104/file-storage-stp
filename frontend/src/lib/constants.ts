export const ROLES = {
  SUPER_ADMIN: "SUPER_ADMIN",
  TENANT_ADMIN: "TENANT_ADMIN",
  MEMBER: "MEMBER",
} as const;

export type Role = (typeof ROLES)[keyof typeof ROLES];

export const FILE_STATUS = {
  PENDING: "PENDING",
  SCANNING: "SCANNING",
  ACTIVE: "ACTIVE",
  INFECTED: "INFECTED",
  UPLOAD_FAILED: "UPLOAD_FAILED",
  PENDING_DELETE: "PENDING_DELETE",
  DELETED: "DELETED",
} as const;

export type FileStatus = (typeof FILE_STATUS)[keyof typeof FILE_STATUS];

export const UPLOAD_STATUS = {
  QUEUED: "queued",
  UPLOADING: "uploading",
  CONFIRMING: "confirming",
  SCANNING: "scanning",
  DONE: "done",
  ERROR: "error",
  CANCELLED: "cancelled",
  INFECTED: "infected",
} as const;

export type UploadStatus = (typeof UPLOAD_STATUS)[keyof typeof UPLOAD_STATUS];

export const UPLOAD_FAILURE_STATUSES: readonly UploadStatus[] = [
  UPLOAD_STATUS.ERROR,
  UPLOAD_STATUS.INFECTED,
  UPLOAD_STATUS.CANCELLED,
];

export const SSE_ERROR_CODE = {
  SCAN_TIMEOUT: "SCAN_TIMEOUT",
  STREAM_LOST: "STREAM_LOST",
  STREAM_PARSE_ERROR: "STREAM_PARSE_ERROR",
} as const;

export type SseErrorCode = (typeof SSE_ERROR_CODE)[keyof typeof SSE_ERROR_CODE];

export const MULTIPART_SESSION_STATUS = {
  IN_PROGRESS: "IN_PROGRESS",
  COMPLETED: "COMPLETED",
  ABORTED: "ABORTED",
} as const;

export type MultipartSessionStatus =
  (typeof MULTIPART_SESSION_STATUS)[keyof typeof MULTIPART_SESSION_STATUS];

export const UPLOAD_MESSAGES = {
  VIRUS_DETECTED: "Virus detected in file",
  PROCESSING_FAILED: "Upload failed during processing",
  SCAN_TIMEOUT: "Scan timeout — please retry",
  SCAN_CONNECTION_LOST: "Lost connection to scan service",
  GENERIC_UPLOAD_FAILED: "Upload failed",
} as const;

export const FILE_SORT_BY = {
  CREATED_AT: "CREATED_AT",
  ORIGINAL_NAME: "ORIGINAL_NAME",
  SIZE_BYTES: "SIZE_BYTES",
} as const;

export type FileSortBy = (typeof FILE_SORT_BY)[keyof typeof FILE_SORT_BY];

export const SORT_DIRECTION = {
  ASC: "ASC",
  DESC: "DESC",
} as const;

export type SortDirection = (typeof SORT_DIRECTION)[keyof typeof SORT_DIRECTION];

export const CHUNK_SIZE = 5 * 1024 * 1024; // 5 MB
export const MAX_FILE_SIZE = 4 * 1024 * 1024 * 1024; // 4 GiB
export const RESUMABLE_THRESHOLD = 100 * 1024 * 1024; // 100 MB — use chunked above this

const API_PREFIX = "/api/v1";

export const API_ROUTES = {
  AUTH: {
    LOGIN: `${API_PREFIX}/auth/signin`,
    LOGOUT: `${API_PREFIX}/auth/logout`,
    REFRESH: `${API_PREFIX}/auth/refresh`,
    ME: `${API_PREFIX}/auth/me`,
    VERIFY_EMAIL: `${API_PREFIX}/auth/verify-email`,
    FORGOT_PASSWORD: `${API_PREFIX}/auth/forgot-password`,
    VERIFY_RESET_TOKEN: `${API_PREFIX}/auth/verify-reset-token`,
    RESET_PASSWORD: `${API_PREFIX}/auth/reset-password`,
    CHANGE_PASSWORD: `${API_PREFIX}/auth/change-password`,
    RESEND_VERIFICATION: `${API_PREFIX}/auth/resend-verification`,
  },
  FILES: {
    LIST: `${API_PREFIX}/files`,
    BY_ID: (id: string) => `${API_PREFIX}/files/${id}`,
    DIRECT_DELETE: (id: string) => `${API_PREFIX}/files/${id}/direct`,
    RESTORE: (id: string) => `${API_PREFIX}/files/${id}/restore`,
    PRESIGNED_GET_URL: (id: string) => `${API_PREFIX}/files/${id}/presigned-get-url`,
    DOWNLOAD: (id: string) => `${API_PREFIX}/files/${id}/download`,
    STATUS_STREAM: (id: string) => `${API_PREFIX}/files/${id}/status/stream`,
    CONFIRM_UPLOAD: (id: string) => `${API_PREFIX}/files/${id}/confirm-upload`,
    MOVE: (id: string) => `${API_PREFIX}/files/${id}/move`,
  },
  UPLOADS: {
    PRESIGNED_INIT: `${API_PREFIX}/uploads/presigned`,
    MULTIPART_INIT: `${API_PREFIX}/uploads/multipart/init`,
    MULTIPART_SESSION: (uploadId: string) => `${API_PREFIX}/uploads/multipart/${uploadId}`,
    MULTIPART_PART_URL: (uploadId: string) => `${API_PREFIX}/uploads/multipart/${uploadId}/part-url`,
    MULTIPART_COMPLETE_PART: (uploadId: string, partNumber: number) =>
      `${API_PREFIX}/uploads/multipart/${uploadId}/parts/${partNumber}/complete`,
    MULTIPART_COMPLETE: (uploadId: string) => `${API_PREFIX}/uploads/multipart/${uploadId}/complete`,
    MULTIPART_ABORT: (uploadId: string) => `${API_PREFIX}/uploads/multipart/${uploadId}`,
    BULK: `${API_PREFIX}/uploads/bulk`,
  },
  FOLDERS: {
    ROOT: `${API_PREFIX}/folders/root`,
    CHILDREN: (id: string) => `${API_PREFIX}/folders/${id}/children`,
    CREATE: `${API_PREFIX}/folders`,
    BY_ID: (id: string) => `${API_PREFIX}/folders/${id}`,
    BREADCRUMBS: (id: string) => `${API_PREFIX}/folders/${id}/breadcrumbs`,
    MOVE: (id: string) => `${API_PREFIX}/folders/${id}/move`,
    ADMIN_ITEMS: `${API_PREFIX}/folders/admin/items`,
  },
  TENANTS: {
    LIST: `${API_PREFIX}/tenants`,
    BY_ID: (id: string) => `${API_PREFIX}/tenants/${id}`,
    OPTIONS: `${API_PREFIX}/tenants/options`,
    ME: `${API_PREFIX}/tenants/me`,
    ACTIVATE: (id: string) => `${API_PREFIX}/tenants/${id}/activate`,
  },
  USERS: {
    LIST: `${API_PREFIX}/users`,
    TENANT: `${API_PREFIX}/users/tenant`,
    BY_ID: (id: string) => `${API_PREFIX}/users/${id}`,
    PASSWORD: (id: string) => `${API_PREFIX}/users/${id}/password`,
    STORAGE: (id: string) => `${API_PREFIX}/users/${id}/storage`,
    ACTIVATE: (id: string) => `${API_PREFIX}/users/${id}/activate`,
  },
  SHARES: {
    ADMIN_LIST: `${API_PREFIX}/shares/admin`,
    ADMIN_REVOKE: (id: string) => `${API_PREFIX}/shares/admin/${id}`,
  },
  AUDIT_LOGS: {
    LIST: `${API_PREFIX}/audit-logs`,
    STATS: `${API_PREFIX}/audit-logs/stats`,
    EXPORT: `${API_PREFIX}/audit-logs/export`,
  },
  USERS_SEARCH: `${API_PREFIX}/users/search`,
  SEARCH: `${API_PREFIX}/search`,
  RAG: {
    SESSIONS: `${API_PREFIX}/rag/chat/sessions`,
    SESSION_MESSAGES: (sessionId: string) => `${API_PREFIX}/rag/chat/sessions/${sessionId}/messages`,
    SESSION_BY_ID: (sessionId: string) => `${API_PREFIX}/rag/chat/sessions/${sessionId}`,
  },
} as const;

export const DEFAULT_PAGE_SIZE = 20;
export const MAX_PAGE_SIZE = 100;
export const GB = 1024 * 1024 * 1024;

export const DANGEROUS_EXTENSIONS = [
  "exe", "bat", "cmd", "sh", "php", "jsp",
] as const;

/**
 * MIME types backend will route through ClamAV when file size is below {@link VIRUS_SCAN_SIZE_LIMIT}.
 * Mirrors `app.upload.virus-scan.scan-mime-types` in UploadProperties.java — keep in sync.
 * Anything outside this set (or at/above the size threshold) bypasses scanning entirely.
 */
export const SCANNABLE_MIME_TYPES = new Set([
  // Documents
  "application/pdf",
  "application/msword",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "application/vnd.ms-excel",
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  "application/vnd.ms-powerpoint",
  "application/vnd.openxmlformats-officedocument.presentationml.presentation",
  "application/rtf",
  "text/rtf",
  "text/plain",
  "text/csv",
  "text/html",
  "application/xml",
  "text/xml",
  // Archives / compressed
  "application/zip",
  "application/x-zip-compressed",
  "application/x-rar-compressed",
  "application/x-7z-compressed",
  "application/x-tar",
  "application/gzip",
  "application/x-bzip2",
  // Executables and installers
  "application/x-msdownload",
  "application/x-executable",
  "application/x-msi",
  "application/x-deb",
  "application/x-rpm",
  "application/java-archive",
  "application/octet-stream",
  // Scripts
  "application/javascript",
  "text/javascript",
  "application/x-sh",
  "text/x-python",
  "application/x-python-code",
  // SVG (can embed scripts)
  "image/svg+xml",
]);

/** Files at or above this size bypass ClamAV — mirrors UploadProperties.VirusScan.bypassScanSizeMb (50 MB). */
export const VIRUS_SCAN_SIZE_LIMIT = 50 * 1024 * 1024;

export const ALLOWED_MIME_LABEL = "All file types supported · Max 4 GB per file";

/**
 * Returns true when the backend will route this file through ClamAV.
 * Must mirror FileUploadValidator.shouldBypassScan logic.
 */
export function willBeVirusScanned(file: File): boolean {
  if (file.size >= VIRUS_SCAN_SIZE_LIMIT) return false;
  const mime = (file.type || "application/octet-stream").split(";")[0].trim().toLowerCase();
  return SCANNABLE_MIME_TYPES.has(mime);
}

/**
 * Only validates file size (MIME is no longer blocked at the frontend).
 * Backend will reject invalid MIMEs at the init step if needed.
 *
 * Returns null if valid, or an error string if the file is too large.
 */
export function validateFileSize(file: File): string | null {
  if (file.size > MAX_FILE_SIZE) {
    const gb = (file.size / GB).toFixed(1);
    const limitGb = MAX_FILE_SIZE / GB;
    return `"${file.name}" is ${gb} GB — files larger than ${limitGb} GB cannot be uploaded.`;
  }
  return null;
}

/** Validate a file before adding to the upload queue. Returns error string or null. */
export function validateUploadFile(file: File): string | null {
  return validateFileSize(file);
}
