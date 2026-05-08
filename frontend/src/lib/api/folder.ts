import { apiClient } from "@/lib/api-client";
import { API_ROUTES } from "@/lib/constants";
import { toAppError } from "@/lib/api/core/api-error";
import type { ApiResponse, PageResponse } from "@/types/api.types";
import type {
  FolderItem,
  FolderMetadataResponse,
  ListFolderParams,
  FolderBreadcrumb,
  AdminListFolderParams,
} from "@/types/folder/folder";

export async function listFolderItems(
  folderId?: string | null,
  params?: ListFolderParams
): Promise<PageResponse<FolderItem>> {
  try {
    const route = folderId ? API_ROUTES.FOLDERS.CHILDREN(folderId) : API_ROUTES.FOLDERS.ROOT;
    // Only send params that are defined to avoid backend validation errors
    const cleanParams: Record<string, string | number> = {};
    if (params?.page) cleanParams.page = params.page;
    if (params?.size) cleanParams.size = params.size;
    if (params?.sort) cleanParams.sort = params.sort;
    if (params?.sortOrder) cleanParams.sortOrder = params.sortOrder;
    if (params?.search) cleanParams.search = params.search;
    if (params?.type) cleanParams.type = params.type;

    const response = await apiClient.get<ApiResponse<PageResponse<FolderItem>>>(route, { params: cleanParams });
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to fetch folders");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function createFolder(
  name: string,
  parentId: string | null
): Promise<FolderMetadataResponse> {
  try {
    const response = await apiClient.post<ApiResponse<FolderMetadataResponse>>(
      API_ROUTES.FOLDERS.CREATE,
      { name, parentId }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to create folder");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function getFolderDetails(
  folderId: string
): Promise<FolderMetadataResponse> {
  try {
    const response = await apiClient.get<ApiResponse<FolderMetadataResponse>>(
      API_ROUTES.FOLDERS.BY_ID(folderId)
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to fetch folder details");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function getFolderBreadcrumbs(
  folderId: string
): Promise<FolderBreadcrumb[]> {
  try {
    const response = await apiClient.get<{ data: FolderBreadcrumb[] }>(
      API_ROUTES.FOLDERS.BREADCRUMBS(folderId)
    );
    return response.data.data ?? [];
  } catch (error) {
    throw toAppError(error);
  }
}

export async function renameFolder(
  folderId: string,
  name: string
): Promise<FolderMetadataResponse> {
  try {
    const response = await apiClient.patch<ApiResponse<FolderMetadataResponse>>(
      API_ROUTES.FOLDERS.BY_ID(folderId),
      { name }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to rename folder");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function moveFolder(
  folderId: string,
  parentId: string | null
): Promise<FolderMetadataResponse> {
  try {
    const response = await apiClient.patch<ApiResponse<FolderMetadataResponse>>(
      API_ROUTES.FOLDERS.MOVE(folderId),
      { parentId }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to move folder");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function deleteFolder(folderId: string): Promise<void> {
  try {
    const response = await apiClient.delete<ApiResponse<void>>(
      API_ROUTES.FOLDERS.BY_ID(folderId)
    );
    // Status 204 typically doesn't have a data payload to check `success` on,
    // but our apiClient might wrap it or the backend might return 200 with an empty body.
    // If backend uses 204, apiClient.delete resolves without error.
  } catch (error) {
    throw toAppError(error);
  }
}

export async function getAdminFolderItems(
  params?: AdminListFolderParams
): Promise<PageResponse<FolderItem>> {
  try {
    const cleanParams: Record<string, string | number> = {};
    if (params?.tenantId) cleanParams.tenantId = params.tenantId;
    if (params?.type) cleanParams.type = params.type;
    if (params?.parentId) cleanParams.parentId = params.parentId;
    if (params?.status) cleanParams.status = params.status;
    if (params?.createdBy) cleanParams.createdBy = params.createdBy;
    if (params?.search) cleanParams.search = params.search;
    if (params?.page) cleanParams.page = params.page;
    if (params?.limit) cleanParams.limit = params.limit;
    if (params?.sortBy) cleanParams.sortBy = params.sortBy;
    if (params?.sortDirection) cleanParams.sortDirection = params.sortDirection;

    const response = await apiClient.get<ApiResponse<PageResponse<FolderItem>>>(
      API_ROUTES.FOLDERS.ADMIN_ITEMS,
      { params: cleanParams }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to fetch admin folders");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}
