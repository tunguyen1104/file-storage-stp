import { apiClient } from "@/lib/api-client";
import { API_ROUTES } from "@/lib/constants";
import { toAppError } from "@/lib/api/core/api-error";
import type { ApiResponse, PageResponse } from "@/types/api.types";
import type {
  FileResponse,
  FileMetadataResponse,
  PresignedGetUrlResponse,
  PresignedDownloadResponse,
  ListFilesParams,
} from "@/types/file/file";

export async function listFiles(
  params?: ListFilesParams
): Promise<PageResponse<FileMetadataResponse>> {
  try {
    const response = await apiClient.get<ApiResponse<PageResponse<FileMetadataResponse>>>(
      API_ROUTES.FILES.LIST,
      { params }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to fetch files");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function getFileMetadata(
  fileId: string
): Promise<FileMetadataResponse> {
  try {
    const response = await apiClient.get<ApiResponse<FileMetadataResponse>>(
      API_ROUTES.FILES.BY_ID(fileId)
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to fetch file metadata");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function deleteFile(fileId: string): Promise<FileResponse> {
  try {
    const response = await apiClient.delete<ApiResponse<FileResponse>>(
      API_ROUTES.FILES.BY_ID(fileId)
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to delete file");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function deleteFileDirect(fileId: string): Promise<void> {
  try {
    await apiClient.delete(API_ROUTES.FILES.DIRECT_DELETE(fileId));
  } catch (error) {
    throw toAppError(error);
  }
}

export async function restoreFile(fileId: string): Promise<FileResponse> {
  try {
    const response = await apiClient.post<ApiResponse<FileResponse>>(
      API_ROUTES.FILES.RESTORE(fileId)
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to restore file");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function getPresignedGetUrl(
  fileId: string,
  ttlMinutes?: number
): Promise<PresignedGetUrlResponse> {
  try {
    const response = await apiClient.get<ApiResponse<PresignedGetUrlResponse>>(
      API_ROUTES.FILES.PRESIGNED_GET_URL(fileId),
      { params: ttlMinutes !== undefined ? { ttlMinutes } : undefined }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to get presigned URL");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function downloadFile(
  fileId: string,
  ttlMinutes?: number
): Promise<PresignedDownloadResponse> {
  try {
    const response = await apiClient.get<ApiResponse<PresignedDownloadResponse>>(
      API_ROUTES.FILES.DOWNLOAD(fileId),
      { params: ttlMinutes !== undefined ? { ttlMinutes } : undefined }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to get download URL");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function moveFile(fileId: string, folderId: string | null): Promise<FileResponse> {
  try {
    const response = await apiClient.patch<ApiResponse<FileResponse>>(
      API_ROUTES.FILES.MOVE(fileId),
      { folderId }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to move file");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function renameFile(fileId: string, originalName: string): Promise<FileMetadataResponse> {
  try {
    const response = await apiClient.patch<ApiResponse<FileMetadataResponse>>(
      API_ROUTES.FILES.BY_ID(fileId),
      { originalName }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to rename file");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}
