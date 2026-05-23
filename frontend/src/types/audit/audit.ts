export type AuditAction =
  | "LOGIN_SUCCESS"
  | "LOGIN_FAILED"
  | "LOGOUT"
  | "PASSWORD_CHANGED"
  | "FILE_UPLOAD"
  | "FILE_DOWNLOAD"
  | "FILE_DELETE"
  | "FILE_RESTORE"
  | "FILE_PURGED"
  | "FOLDER_CREATE"
  | "FOLDER_DELETE"
  | "TENANT_CREATED"
  | "TENANT_DEACTIVATED"
  | "USER_CREATED"
  | "USER_DEACTIVATED"
  | "USER_ROLE_CHANGED"
  | "QUOTA_EXCEEDED"
  | "UPLOAD_FAILED"
  | "VIRUS_DETECTED";

export type AuditSeverity = "ERROR" | "WARNING" | "INFO" | "SUCCESS";

export type AuditLogSortBy = "CREATED_AT" | "ACTION";
export type AuditSortDirection = "ASC" | "DESC";

export type ActorRole =
  | "SUPER_ADMIN"
  | "TENANT_ADMIN"
  | "MEMBER"
  | "SYSTEM"
  | "ANONYMOUS";

export interface ListAuditLogsParams {
  userEmail?: string;
  action?: AuditAction[];
  fileId?: string;
  tenantId?: string;
  from?: string;
  to?: string;
  ipAddress?: string;
  userAgent?: string;
  page?: number;
  limit?: number;
  sortBy?: AuditLogSortBy;
  sortDir?: AuditSortDirection;
}

export type ExportAuditLogsParams = Omit<ListAuditLogsParams, "page" | "limit">;

export interface SearchUsersParams {
  email: string;
  tenantId?: string;
}

export interface AuditLogEntry {
  id: string;
  tenantId?: string;
  userId?: string | null;
  userEmail?: string | null;
  fileId?: string | null;
  action: AuditAction;
  severity: AuditSeverity;
  actorRole?: ActorRole | null;
  ipAddress?: string | null;
  userAgent?: string | null;
  requestId?: string | null;
  extraData?: Record<string, unknown> | null;
  createdAt: string;
}

export interface AuditLogStats {
  total: number;
  severityCounts: Record<AuditSeverity, number>;
}

export interface UserSearchResult {
  id: string;
  email: string;
}

export const AUDIT_ACTIONS: AuditAction[] = [
  "LOGIN_SUCCESS",
  "LOGIN_FAILED",
  "LOGOUT",
  "PASSWORD_CHANGED",
  "FILE_UPLOAD",
  "FILE_DOWNLOAD",
  "FILE_DELETE",
  "FILE_RESTORE",
  "FILE_PURGED",
  "FOLDER_CREATE",
  "FOLDER_DELETE",
  "TENANT_CREATED",
  "TENANT_DEACTIVATED",
  "USER_CREATED",
  "USER_DEACTIVATED",
  "USER_ROLE_CHANGED",
  "QUOTA_EXCEEDED",
  "UPLOAD_FAILED",
  "VIRUS_DETECTED",
];

export const AUDIT_ACTION_LABELS: Record<AuditAction, string> = {
  LOGIN_SUCCESS: "Đăng nhập thành công",
  LOGIN_FAILED: "Đăng nhập thất bại",
  LOGOUT: "Đăng xuất",
  PASSWORD_CHANGED: "Đổi mật khẩu",
  FILE_UPLOAD: "Tải lên tệp",
  FILE_DOWNLOAD: "Tải xuống tệp",
  FILE_DELETE: "Xoá tệp (mềm)",
  FILE_RESTORE: "Khôi phục tệp",
  FILE_PURGED: "Xoá tệp vĩnh viễn",
  FOLDER_CREATE: "Tạo thư mục",
  FOLDER_DELETE: "Xoá thư mục",
  TENANT_CREATED: "Tạo tổ chức",
  TENANT_DEACTIVATED: "Vô hiệu hoá tổ chức",
  USER_CREATED: "Tạo người dùng",
  USER_DEACTIVATED: "Vô hiệu hoá người dùng",
  USER_ROLE_CHANGED: "Đổi vai trò người dùng",
  QUOTA_EXCEEDED: "Vượt hạn mức",
  UPLOAD_FAILED: "Tải lên thất bại",
  VIRUS_DETECTED: "Phát hiện virus",
};

export const AUDIT_ACTION_GROUPS: { label: string; actions: AuditAction[] }[] = [
  {
    label: "Xác thực",
    actions: ["LOGIN_SUCCESS", "LOGIN_FAILED", "LOGOUT", "PASSWORD_CHANGED"],
  },
  {
    label: "Tệp tin",
    actions: [
      "FILE_UPLOAD",
      "FILE_DOWNLOAD",
      "FILE_DELETE",
      "FILE_RESTORE",
      "FILE_PURGED",
    ],
  },
  {
    label: "Thư mục",
    actions: ["FOLDER_CREATE", "FOLDER_DELETE"],
  },
  {
    label: "Quản trị",
    actions: [
      "TENANT_CREATED",
      "TENANT_DEACTIVATED",
      "USER_CREATED",
      "USER_DEACTIVATED",
      "USER_ROLE_CHANGED",
    ],
  },
  {
    label: "Hệ thống",
    actions: ["QUOTA_EXCEEDED", "UPLOAD_FAILED", "VIRUS_DETECTED"],
  },
];

export const AUDIT_SEVERITIES: AuditSeverity[] = [
  "ERROR",
  "WARNING",
  "INFO",
  "SUCCESS",
];

export const AUDIT_SEVERITY_LABELS: Record<AuditSeverity, string> = {
  ERROR: "Lỗi",
  WARNING: "Cảnh báo",
  INFO: "Thông tin",
  SUCCESS: "Thành công",
};
