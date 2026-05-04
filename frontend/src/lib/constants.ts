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
    UPLOAD_SINGLE: `${API_PREFIX}/uploads/single`,
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
