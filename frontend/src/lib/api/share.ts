import { apiClient } from "@/lib/api-client";
import { toAppError } from "@/lib/api/core/api-error";
import type { ApiResponse, PageResponse } from "@/types/api.types";
import type {
  ShareResponse,
  ShareWithResourceResponse,
  SharedWithMeResponse,
  ShareExpirationResponse,
} from "@/types/share/share";

const BASE_PATH = "/api/v1/shares";

// 1. Share File với User
export async function shareFileWithUser(
  fileId: string,
  sharedWithUserEmail: string,
  expireTime: number
): Promise<ShareResponse> {
  try {
    const response = await apiClient.post<ApiResponse<ShareResponse>>(`${BASE_PATH}/files`, {
      fileId,
      sharedWithUserEmail,
      expireTime,
    });
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to share file with user");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

// 2. Share Folder với User
export async function shareFolderWithUser(
  folderId: string,
  sharedWithUserEmail: string,
  permission: "VIEW" | "EDIT",
  expireTime: number
): Promise<ShareResponse> {
  try {
    const response = await apiClient.post<ApiResponse<ShareResponse>>(`${BASE_PATH}/folders`, {
      folderId,
      sharedWithUserEmail,
      permission,
      expireTime,
    });
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to share folder with user");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

// 3. Share File với Tenant
export async function shareFileWithTenant(
  resourceId: string,
  expireTime: number,
  tenantId?: string | null
): Promise<ShareResponse> {
  try {
    const body: Record<string, any> = { resourceId, expireTime };
    if (tenantId) body.tenantId = tenantId;

    const response = await apiClient.post<ApiResponse<ShareResponse>>(`${BASE_PATH}/files/tenant`, body);
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to share file with tenant");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

// 4. Share Folder với Tenant
export async function shareFolderWithTenant(
  resourceId: string,
  expireTime: number,
  tenantId?: string | null
): Promise<ShareResponse> {
  try {
    const body: Record<string, any> = { resourceId, expireTime };
    if (tenantId) body.tenantId = tenantId;

    const response = await apiClient.post<ApiResponse<ShareResponse>>(`${BASE_PATH}/folders/tenant`, body);
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to share folder with tenant");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

// 5. Gia hạn Share
export async function extendShare(
  shareId: string,
  expireTime: number
): Promise<ShareExpirationResponse> {
  try {
    const response = await apiClient.patch<ApiResponse<ShareExpirationResponse>>(
      `${BASE_PATH}/${shareId}/expiration`,
      { expireTime }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to extend share");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

// 6. Lấy danh sách share do tôi tạo
export async function listSharesByMe(params: {
  resourceType?: "file" | "folder";
  status?: string;
  page?: number;
  limit?: number;
}): Promise<PageResponse<ShareWithResourceResponse>> {
  try {
    const response = await apiClient.get<ApiResponse<PageResponse<ShareWithResourceResponse>>>(
      `${BASE_PATH}/by-me`,
      { params }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to fetch shares by me");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

// 7. Lấy danh sách share với tôi
export async function listSharesWithMe(params: {
  resourceType?: "file" | "folder";
  page?: number;
  limit?: number;
}): Promise<PageResponse<SharedWithMeResponse>> {
  try {
    const response = await apiClient.get<ApiResponse<PageResponse<SharedWithMeResponse>>>(
      `${BASE_PATH}/with-me`,
      { params }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to fetch shares with me");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

// 8. Thu hồi share (owner)
export async function revokeShare(shareId: string): Promise<void> {
  try {
    const response = await apiClient.delete<ApiResponse<void>>(`${BASE_PATH}/${shareId}`);
    if (response.data && response.data.success === false) {
      throw new Error(response.data.message ?? "Failed to revoke share");
    }
  } catch (error) {
    throw toAppError(error);
  }
}

// 9. Admin thu hồi share
export async function adminRevokeShare(shareId: string): Promise<void> {
  try {
    const response = await apiClient.delete<ApiResponse<void>>(`${BASE_PATH}/admin/${shareId}`);
    if (response.data && response.data.success === false) {
      throw new Error(response.data.message ?? "Failed to revoke share as admin");
    }
  } catch (error) {
    throw toAppError(error);
  }
}

// 10. Admin bulk revoke share của một user
export async function adminBulkRevoke(
  userId: string,
  scope: "GRANTED" | "RECEIVED" | "BOTH"
): Promise<{
  targetUserId: string;
  scope: string;
  revokedShareIds: string[];
  revokedCount: number;
  auditId: string;
}> {
  try {
    const response = await apiClient.post<
      ApiResponse<{
        targetUserId: string;
        scope: string;
        revokedShareIds: string[];
        revokedCount: number;
        auditId: string;
      }>
    >(`${BASE_PATH}/admin/users/${userId}/revoke`, { scope });
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to bulk revoke shares");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export interface AdminListSharesParams {
  tenantId?: string[];
  sharedBy?: string[];
  resourceType?: "file" | "folder";
  status?: string[];
  page?: number;
  limit?: number;
  sortBy?: string[];
  sortDirection?: string[];
}

export async function adminListShares(
  params?: AdminListSharesParams
): Promise<PageResponse<ShareResponse>> {
  try {
    const searchParams = new URLSearchParams();
    if (params) {
      Object.entries(params).forEach(([key, value]) => {
        if (value === undefined || value === null) return;
        if (Array.isArray(value)) {
          value.forEach((v) => {
            if (v !== undefined && v !== null) {
              searchParams.append(key, String(v));
            }
          });
        } else {
          searchParams.append(key, String(value));
        }
      });
    }

    const response = await apiClient.get<ApiResponse<PageResponse<ShareResponse>>>(
      "/api/v1/shares/admin",
      { params: searchParams }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to fetch admin shares");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

