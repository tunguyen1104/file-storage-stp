import { apiClient } from "@/lib/api-client";
import { API_ROUTES } from "@/lib/constants";
import type { ApiResponse } from "@/types/api.types";
import type {
  CreateTenantRequest,
  ListTenantsParams,
  TenantItem,
  UpdateTenantRequest,
} from "@/types/tenant/tenant";

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

export async function listTenants(params?: ListTenantsParams): Promise<PaginatedResult<TenantItem>> {
  const response = await apiClient.get<ApiResponse<unknown>>(API_ROUTES.TENANTS, {
    params,
  });

  return normalizePaginated<TenantItem>(response.data.data);
}

export async function getTenantById(tenantId: string): Promise<TenantItem | undefined> {
  const response = await apiClient.get<ApiResponse<TenantItem>>(`${API_ROUTES.TENANTS}/${tenantId}`);
  return response.data.data;
}

export async function createTenant(payload: CreateTenantRequest): Promise<TenantItem | undefined> {
  const response = await apiClient.post<ApiResponse<TenantItem>>(API_ROUTES.TENANTS, payload);
  return response.data.data;
}

export async function updateTenant(tenantId: string, payload: UpdateTenantRequest): Promise<TenantItem | undefined> {
  const response = await apiClient.patch<ApiResponse<TenantItem>>(`${API_ROUTES.TENANTS}/${tenantId}`, payload);
  return response.data.data;
}

export async function deactivateTenant(tenantId: string): Promise<TenantItem | undefined> {
  const response = await apiClient.delete<ApiResponse<TenantItem>>(`${API_ROUTES.TENANTS}/${tenantId}`);
  return response.data.data;
}
