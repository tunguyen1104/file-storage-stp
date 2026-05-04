export const ROLES = {
  SUPER_ADMIN: "SUPER_ADMIN",
  TENANT_ADMIN: "TENANT_ADMIN",
  MEMBER: "MEMBER",
} as const;

export type Role = (typeof ROLES)[keyof typeof ROLES];

export const FILE_STATUS = {
  PENDING: "PENDING",
  ACTIVE: "ACTIVE",
  PENDING_DELETE: "PENDING_DELETE",
  DELETED: "DELETED",
  UPLOAD_FAILED: "UPLOAD_FAILED",
} as const;

export type FileStatus = (typeof FILE_STATUS)[keyof typeof FILE_STATUS];

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
export const MAX_FILE_SIZE = 10 * 1024 * 1024 * 1024; // 10 GB
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
    STATUS: (id: string) => `${API_PREFIX}/files/${id}/status`,
    CONFIRM_UPLOAD: (id: string) => `${API_PREFIX}/files/${id}/confirm-upload`,
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
  },
  FOLDERS: {
    ROOT: `${API_PREFIX}/folders/root`,
    CHILDREN: (id: string) => `${API_PREFIX}/folders/${id}/children`,
    CREATE: `${API_PREFIX}/folders`,
    BY_ID: (id: string) => `${API_PREFIX}/folders/${id}`,
    BREADCRUMBS: (id: string) => `${API_PREFIX}/folders/${id}/breadcrumbs`,
    MOVE: (id: string) => `${API_PREFIX}/folders/${id}/move`,
  },
  TENANTS: {
    LIST: `${API_PREFIX}/tenants`,
    BY_ID: (id: string) => `${API_PREFIX}/tenants/${id}`,
    OPTIONS: `${API_PREFIX}/tenants/options`,
  },
  USERS: {
    LIST: `${API_PREFIX}/users`,
    TENANT: `${API_PREFIX}/users/tenant`,
    BY_ID: (id: string) => `${API_PREFIX}/users/${id}`,
    PASSWORD: (id: string) => `${API_PREFIX}/users/${id}/password`,
    STORAGE: (id: string) => `${API_PREFIX}/users/${id}/storage`,
  },
  AUDIT_LOGS: `${API_PREFIX}/audit-logs`,
  SEARCH: `${API_PREFIX}/search`,
} as const;

export const DEFAULT_PAGE_SIZE = 20;
export const MAX_PAGE_SIZE = 100;
export const GB = 1024 * 1024 * 1024;

export const DANGEROUS_EXTENSIONS = [
  "exe", "bat", "cmd", "sh", "php", "jsp",
] as const;

/**
 * Mirrors backend app.upload.allowed-mime-types (includes risky types that have been added).
 * Keep in sync with application-dev.yml / application-prod.yml.
 */
export const ALLOWED_MIME_TYPES = new Set([
  // Images
  "image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml",
  // Documents
  "application/pdf",
  "application/msword",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "application/vnd.ms-excel",
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  "application/vnd.ms-powerpoint",
  "application/vnd.openxmlformats-officedocument.presentationml.presentation",
  // Text
  "text/plain", "text/csv",
  // Video
  "video/mp4", "video/quicktime", "video/x-msvideo",
  // Audio
  "audio/mpeg", "audio/wav", "audio/flac", "audio/ogg",
  // ⚠️ Potentially risky — allowed but always virus-scanned, frontend shows warning toast
  "application/zip", "application/x-zip-compressed",
  "application/x-rar-compressed", "application/x-7z-compressed",
  "application/x-tar", "application/gzip",
  "application/x-msdownload", "application/x-executable",
  "application/x-msi", "application/x-deb", "application/x-rpm",
  "application/java-archive", "application/octet-stream",
]);

/**
 * MIME types that trigger a security warning toast before upload proceeds.
 * Upload still proceeds immediately — NO additional confirmation required from user.
 */
export const RISKY_MIME_TYPES = new Set([
  "application/zip", "application/x-zip-compressed",
  "application/x-rar-compressed", "application/x-7z-compressed",
  "application/x-tar", "application/gzip",
  "application/x-msdownload", "application/x-executable",
  "application/x-msi", "application/x-deb", "application/x-rpm",
  "application/java-archive", "application/octet-stream",
]);

/**
 * File extensions considered dangerous — fallback when browser cannot detect MIME
 * (e.g. file has no extension or browser returns "" for some unusual types).
 */
export const RISKY_EXTENSIONS = new Set([
  "exe", "msi", "bat", "cmd", "com", "scr", "ps1", "vbs",
  "zip", "rar", "7z", "tar", "gz", "iso",
  "deb", "rpm", "jar",
]);

export const ALLOWED_MIME_LABEL =
  "Images · PDF · Word · Excel · PowerPoint · Text/CSV · MP4/MOV/AVI · MP3/WAV/FLAC/OGG · ZIP/EXE (warned)";

/**
 * Check whether a file is potentially dangerous based on MIME + extension.
 * Used to show a warning toast — does NOT block upload.
 */
export function isRiskyFile(file: File): boolean {
  const ext = file.name.split(".").pop()?.toLowerCase() ?? "";
  return RISKY_MIME_TYPES.has(file.type) || RISKY_EXTENSIONS.has(ext);
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
    return `"${file.name}" is ${gb} GB — maximum allowed size is ${MAX_FILE_SIZE / GB} GB.`;
  }
  return null;
}

/** Validate a file before adding to the upload queue. Returns error string or null. */
export function validateUploadFile(file: File): string | null {
  return validateFileSize(file);
}
