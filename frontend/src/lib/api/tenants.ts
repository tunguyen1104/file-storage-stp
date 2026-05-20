import { apiClient } from "@/lib/api-client";
import { API_ROUTES } from "@/lib/constants";
import { toAppError } from "@/lib/api/core/api-error";
import type { ApiResponse, PageResponse } from "@/types/api.types";
import type {
  TenantResponse,
  TenantOptionResponse,
  CreateTenantRequest,
  UpdateTenantRequest,
  ListTenantsParams,
} from "@/types/tenant/tenant";

export async function listTenants(
  params?: ListTenantsParams
): Promise<PageResponse<TenantResponse>> {
  try {
    const response = await apiClient.get<ApiResponse<PageResponse<TenantResponse>>>(
      API_ROUTES.TENANTS.LIST,
      { params }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to fetch tenants");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function listTenantOptions(): Promise<TenantOptionResponse[]> {
  try {
    const response = await apiClient.get<ApiResponse<TenantOptionResponse[]>>(
      API_ROUTES.TENANTS.OPTIONS
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to fetch tenant options");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function getTenantById(tenantId: string): Promise<TenantResponse> {
  try {
    const response = await apiClient.get<ApiResponse<TenantResponse>>(
      API_ROUTES.TENANTS.BY_ID(tenantId)
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to fetch tenant");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function createTenant(
  data: CreateTenantRequest
): Promise<TenantResponse> {
  try {
    const response = await apiClient.post<ApiResponse<TenantResponse>>(
      API_ROUTES.TENANTS.LIST,
      data
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to create tenant");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function updateTenant(
  tenantId: string,
  data: UpdateTenantRequest
): Promise<TenantResponse> {
  try {
    const response = await apiClient.patch<ApiResponse<TenantResponse>>(
      API_ROUTES.TENANTS.BY_ID(tenantId),
      data
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to update tenant");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function deactivateTenant(tenantId: string): Promise<void> {
  try {
    await apiClient.delete(API_ROUTES.TENANTS.BY_ID(tenantId));
  } catch (error) {
    throw toAppError(error);
  }
}

export async function activateTenant(tenantId: string): Promise<TenantResponse> {
  try {
    const response = await apiClient.patch<ApiResponse<TenantResponse>>(
      API_ROUTES.TENANTS.ACTIVATE(tenantId)
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to activate tenant");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function getTenantMe(): Promise<TenantResponse> {
  try {
    const response = await apiClient.get<ApiResponse<TenantResponse>>(
      API_ROUTES.TENANTS.ME
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to fetch current tenant");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}
