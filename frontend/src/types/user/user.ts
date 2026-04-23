import type { Role } from "@/lib/constants";

/** Returned by GET /users/tenant, GET /users/{id}, PATCH /users/{id} */
export type UserResponse = {
  id: string;
  email: string;
  fullName: string;
  role: string;
  tenantId: string | null;
  tenantName: string | null;
  isActive: boolean;
  storageQuotaBytes: number;
  storageUsedBytes: number;
  createdBy: string | null;
  lastLoginAt: string | null;
  passwordChangedAt: string | null;
  createdAt: string;
  updatedAt: string | null;
};

/** Returned by POST /users */
export type UserCreatedResponse = {
  id: string;
  email: string;
  fullName: string;
  role: string;
  tenantId: string | null;
  isActive: boolean;
  storageQuotaBytes: number;
  storageUsedBytes: number;
  createdBy: string | null;
  createdAt: string;
};

/** Returned by GET /users/{id}/storage */
export type UserStorageResponse = {
  userId: string;
  storageQuotaBytes: number;
  storageUsedBytes: number;
  storageAvailableBytes: number;
  usagePercent: number;
};

export type CreateUserRequest = {
  email: string;
  fullName: string;
  /** Role enum code, e.g. "TENANT_ADMIN" */
  roleCode: Role;
  storageQuotaBytes?: number;
  tenantId?: string;
};

export type UpdateUserRequest = {
  fullName?: string;
  email?: string;
  roleCode?: Role;
  storageQuotaBytes?: number;
};

export type ChangeUserPasswordRequest = {
  currentPassword?: string;
  newPassword: string;
  confirmPassword: string;
};

export type ListUsersParams = {
  isActive?: boolean;
  /** Filter by role enum code */
  roleCode?: Role;
  tenantId?: string;
  search?: string;
  page?: number;
  limit?: number;
};
