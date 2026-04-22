export type UserRole = "SUPER_ADMIN" | "TENANT_ADMIN" | "MEMBER";

export type UserItem = {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
  tenantId?: string;
  isActive?: boolean;
  storageQuotaBytes?: number;
  lastLoginAt?: string;
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
