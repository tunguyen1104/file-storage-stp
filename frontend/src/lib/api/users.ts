import { apiClient } from "@/lib/api-client";
import { API_ROUTES } from "@/lib/constants";
import type { ApiResponse } from "@/types/api.types";
import type {
  ChangeUserPasswordRequest,
  CreateUserRequest,
  ListUsersParams,
  UpdateUserRequest,
  UserItem,
  UserStorage,
} from "@/types/user/user";

type PaginatedResult<T> = {
  items: T[];
  currentPage: number;
  totalPages: number;
  totalElements: number;
  pageSize: number;
};

type RawPaginatedShape<T> = {
  items?: T[];
  content?: T[];
  data?: T[];
  pageSize?: number;
  size?: number;
  limit?: number;
  totalPages?: number;
  totalElements?: number;
  total?: number;
  currentPage?: number;
  page?: number;
};

function normalizePaginated<T>(data: unknown): PaginatedResult<T> {
  if (!data || typeof data !== "object") {
    return {
      items: [],
      currentPage: 1,
      totalPages: 1,
      totalElements: 0,
      pageSize: 0,
    };
  }

  const source = data as RawPaginatedShape<T>;
  const items = source.items ?? source.content ?? source.data ?? [];
  const pageSize = source.pageSize ?? source.size ?? source.limit ?? items.length;
  const totalElements = source.totalElements ?? source.total ?? items.length;
  const totalPages = source.totalPages ?? 1;
  const currentPage = source.currentPage ?? source.page ?? 1;

  return {
    items: Array.isArray(items) ? items : [],
    currentPage,
    totalPages,
    totalElements,
    pageSize,
  };
}

export async function listUsers(params?: ListUsersParams): Promise<PaginatedResult<UserItem>> {
  const response = await apiClient.get<ApiResponse<unknown>>(API_ROUTES.USERS, {
    params,
  });

  return normalizePaginated<UserItem>(response.data.data);
}

export async function listTenantUsers(params?: Omit<ListUsersParams, "tenantId">): Promise<PaginatedResult<UserItem>> {
  const response = await apiClient.get<ApiResponse<unknown>>(`${API_ROUTES.USERS}/tenant`, {
    params,
  });

  return normalizePaginated<UserItem>(response.data.data);
}

export async function getUserById(userId: string): Promise<UserItem | undefined> {
  const response = await apiClient.get<ApiResponse<UserItem>>(`${API_ROUTES.USERS}/${userId}`);
  return response.data.data;
}

export async function createUser(payload: CreateUserRequest): Promise<UserItem | undefined> {
  const response = await apiClient.post<ApiResponse<UserItem>>(API_ROUTES.USERS, payload);
  return response.data.data;
}

export async function updateUser(userId: string, payload: UpdateUserRequest): Promise<UserItem | undefined> {
  const response = await apiClient.patch<ApiResponse<UserItem>>(`${API_ROUTES.USERS}/${userId}`, payload);
  return response.data.data;
}

export async function deactivateUser(userId: string): Promise<UserItem | undefined> {
  const response = await apiClient.delete<ApiResponse<UserItem>>(`${API_ROUTES.USERS}/${userId}`);
  return response.data.data;
}

export async function changeUserPassword(userId: string, payload: ChangeUserPasswordRequest): Promise<void> {
  await apiClient.patch(`${API_ROUTES.USERS}/${userId}/password`, payload);
}

export async function getUserStorage(userId: string): Promise<UserStorage | undefined> {
  const response = await apiClient.get<ApiResponse<UserStorage>>(`${API_ROUTES.USERS}/${userId}/storage`);
  return response.data.data;
}
