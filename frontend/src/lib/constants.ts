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
} as const;

export type FileStatus = (typeof FILE_STATUS)[keyof typeof FILE_STATUS];

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
  },
  FILES: `${API_PREFIX}/files`,
  TENANTS: `${API_PREFIX}/tenants`,
  USERS: `${API_PREFIX}/users`,
  AUDIT_LOGS: `${API_PREFIX}/audit-logs`,
  SEARCH: `${API_PREFIX}/search`,
} as const;

export const DEFAULT_PAGE_SIZE = 20;
export const MAX_PAGE_SIZE = 100;

export const DANGEROUS_EXTENSIONS = [
  "exe", "bat", "cmd", "sh", "php", "jsp",
] as const;
