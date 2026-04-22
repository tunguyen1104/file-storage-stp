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

export const API_ROUTES = {
  AUTH: {
    LOGIN: `/auth/signin`,
    LOGOUT: `/auth/logout`,
    REFRESH: `/auth/refresh`,
    ME: `/auth/me`,
    VERIFY_EMAIL: `/auth/verify-email`,
    FORGOT_PASSWORD: `/auth/forgot-password`,
    VERIFY_RESET_TOKEN: `/auth/verify-reset-token`,
    RESET_PASSWORD: `/auth/reset-password`,
    CHANGE_PASSWORD: `/auth/change-password`,
    RESEND_VERIFICATION: `/auth/resend-verification`,
  },
  FILES: `/files`,
  TENANTS: `/tenants`,
  USERS: `/users`,
  AUDIT_LOGS: `/audit-logs`,
  SEARCH: `/search`,
} as const;

export const DEFAULT_PAGE_SIZE = 20;
export const MAX_PAGE_SIZE = 100;

export const DANGEROUS_EXTENSIONS = [
  "exe", "bat", "cmd", "sh", "php", "jsp",
] as const;
