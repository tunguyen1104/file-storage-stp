import { apiClient } from "@/lib/api-client";
import { API_ROUTES } from "@/lib/constants";
import { toAppError } from "@/lib/api/core/api-error";
import type { ApiResponse, PageResponse } from "@/types/api.types";
import type {
  UserResponse,
  UserCreatedResponse,
  UserStorageResponse,
  CreateUserRequest,
  UpdateUserRequest,
  ChangeUserPasswordRequest,
  ListUsersParams,
} from "@/types/user/user";

export async function listUsers(
  params?: ListUsersParams
): Promise<PageResponse<UserResponse>> {
  try {
    const response = await apiClient.get<ApiResponse<PageResponse<UserResponse>>>(
      API_ROUTES.USERS.LIST,
      { params }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to fetch users");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function listTenantUsers(
  params?: Omit<ListUsersParams, "tenantId">
): Promise<PageResponse<UserResponse>> {
  try {
    const response = await apiClient.get<ApiResponse<PageResponse<UserResponse>>>(
      API_ROUTES.USERS.TENANT,
      { params }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to fetch users");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function getUserById(userId: string): Promise<UserResponse> {
  try {
    const response = await apiClient.get<ApiResponse<UserResponse>>(
      API_ROUTES.USERS.BY_ID(userId)
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to fetch user");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function getUserStorage(userId: string): Promise<UserStorageResponse> {
  try {
    const response = await apiClient.get<ApiResponse<UserStorageResponse>>(
      API_ROUTES.USERS.STORAGE(userId)
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to fetch user storage");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function createUser(
  data: CreateUserRequest
): Promise<UserCreatedResponse> {
  try {
    const response = await apiClient.post<ApiResponse<UserCreatedResponse>>(
      API_ROUTES.USERS.LIST,
      data
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to create user");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function updateUser(
  userId: string,
  data: UpdateUserRequest
): Promise<UserResponse> {
  try {
    const response = await apiClient.patch<ApiResponse<UserResponse>>(
      API_ROUTES.USERS.BY_ID(userId),
      data
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to update user");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function deactivateUser(userId: string): Promise<void> {
  try {
    await apiClient.delete(API_ROUTES.USERS.BY_ID(userId));
  } catch (error) {
    throw toAppError(error);
  }
}

export async function changeUserPassword(
  userId: string,
  data: ChangeUserPasswordRequest
): Promise<void> {
  try {
    await apiClient.patch(API_ROUTES.USERS.PASSWORD(userId), data);
  } catch (error) {
    throw toAppError(error);
  }
}
