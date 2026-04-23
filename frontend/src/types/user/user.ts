export type UserRole = "SUPER_ADMIN" | "TENANT_ADMIN" | "MEMBER";

export type RoleSummary = {
  id: string;
  code: UserRole;
  name: string;
};

export type UserItem = {
  id: string;
  email: string;
  fullName: string;
  role: UserRole | RoleSummary;
  tenantId?: string;
  tenantName?: string;
  isActive?: boolean;
  storageQuotaBytes?: number;
  storageUsedBytes?: number;
  createdBy?: string;
  lastLoginAt?: string;
  passwordChangedAt?: string;
  createdAt?: string;
  updatedAt?: string;
};

export type UserStorage = {
  userId: string;
  storageQuotaBytes: number;
  storageUsedBytes: number;
  storageAvailableBytes: number;
  usagePercent: number;
};

export type ListUsersParams = {
  isActive?: boolean;
  roleId?: string;
  tenantId?: string;
  search?: string;
  page?: number;
  limit?: number;
};

export type CreateUserRequest = {
  email: string;
  fullName: string;
  roleId: string;
  storageQuotaBytes?: number;
  tenantId?: string;
};

export type UpdateUserRequest = {
  fullName?: string;
  email?: string;
  roleId?: string;
  storageQuotaBytes?: number;
};

export type ChangeUserPasswordRequest = {
  currentPassword?: string;
  newPassword: string;
  confirmPassword: string;
};
