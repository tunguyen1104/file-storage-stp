import axios from "axios";
import { apiClient } from "@/lib/api-client";
import { API_ROUTES } from "@/lib/constants";
import { toAppError } from "@/lib/api/core/api-error";
import type { ApiResponse } from "@/types/api.types";
import type {
  PresignedInitResponse,
  MultipartInitResponse,
  PartUrlResponse,
  MultipartSessionResponse,
  FileStatusResponse,
} from "@/types/upload/upload.types";

/**
 * Axios instance for direct PUT calls to MinIO presigned URLs.
 * Must NOT use the BFF proxy or CSRF interceptors — these are direct S3 calls.
 */
export const minioClient = axios.create();

// ─── Presigned Upload ─────────────────────────────────────────────────────────

/**
 * Step 1 of presigned upload: initialize and get a presigned PUT URL.
 * ⚠️ Request field: "originalName" (not "fileName") — matches PresignedUploadInitRequest.java
 */
export async function initPresignedUpload(payload: {
  originalName: string;
  mimeType: string;
  sizeBytes: number;
  folderId?: string;
}): Promise<PresignedInitResponse> {
  try {
    const response = await apiClient.post<ApiResponse<PresignedInitResponse>>(
      API_ROUTES.UPLOADS.PRESIGNED_INIT,
      payload
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to initialize presigned upload");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

/**
 * Step 3 of presigned upload: confirm the file was uploaded to MinIO.
 * Backend verifies size/MIME, transitions PENDING → SCANNING, triggers virus scan.
 */
export async function confirmUpload(fileId: string): Promise<void> {
  try {
    await apiClient.post(API_ROUTES.FILES.CONFIRM_UPLOAD(fileId));
  } catch (error) {
    throw toAppError(error);
  }
}

/**
 * Poll file status after confirm / multipart complete.
 * Response field is `fileId` (not `id`) — per FileRestMapper mapping.
 */
export async function getFileStatus(fileId: string): Promise<FileStatusResponse> {
  try {
    const response = await apiClient.get<ApiResponse<FileStatusResponse>>(
      API_ROUTES.FILES.STATUS(fileId)
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to get file status");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

// ─── Multipart Upload ─────────────────────────────────────────────────────────

/**
 * Step 1 of multipart upload: initialize session.
 * ⚠️ Request fields:
 *   - "originalName" (not "fileName") — matches InitMultipartUploadRequest.java
 *   - "totalSizeBytes" (not "sizeBytes") — matches InitMultipartUploadRequest.java
 */
export async function initMultipartUpload(payload: {
  originalName: string;
  mimeType: string;
  totalSizeBytes: number;
  totalParts: number;
  folderId?: string;
}): Promise<MultipartInitResponse> {
  try {
    const response = await apiClient.post<ApiResponse<MultipartInitResponse>>(
      API_ROUTES.UPLOADS.MULTIPART_INIT,
      payload
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to initialize multipart upload");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

/**
 * Get a presigned PUT URL for a single chunk.
 * Called fresh before each chunk upload to avoid 30-minute TTL expiry.
 */
export async function getPartUrl(
  uploadId: string,
  partNumber: number
): Promise<PartUrlResponse> {
  try {
    const response = await apiClient.get<ApiResponse<PartUrlResponse>>(
      API_ROUTES.UPLOADS.MULTIPART_PART_URL(uploadId),
      { params: { partNumber } }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to get part URL");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

/**
 * Mark a single chunk as complete.
 * ⚠️ Request body: { etag } only — matches CompletePartRequest.java
 */
export async function completePart(
  uploadId: string,
  partNumber: number,
  etag: string
): Promise<void> {
  try {
    await apiClient.post(
      API_ROUTES.UPLOADS.MULTIPART_COMPLETE_PART(uploadId, partNumber),
      { etag }
    );
  } catch (error) {
    throw toAppError(error);
  }
}

/**
 * Finalize multipart upload by assembling all parts.
 * ⚠️ Request body: { parts: [{partNumber, etag}] } — matches CompleteMultipartRequest.java
 * ⚠️ Backend requires parts.length === totalParts declared at init (PARTS_INCOMPLETE error otherwise).
 *    Must wait for ALL chunks to upload and completePart before calling this.
 */
export async function completeMultipart(
  uploadId: string,
  parts: { partNumber: number; etag: string }[]
): Promise<void> {
  try {
    await apiClient.post(
      API_ROUTES.UPLOADS.MULTIPART_COMPLETE(uploadId),
      { parts }
    );
  } catch (error) {
    throw toAppError(error);
  }
}

/**
 * Abort a multipart upload session.
 * Cleans up MinIO and marks the backend session as ABORTED.
 */
export async function abortMultipart(uploadId: string): Promise<void> {
  try {
    await apiClient.delete(API_ROUTES.UPLOADS.MULTIPART_ABORT(uploadId));
  } catch (error) {
    throw toAppError(error);
  }
}

/**
 * Fetch the current multipart session (used for resume after error/refresh).
 * ⚠️ Response does NOT contain "fileName" — stored separately in PendingMultipartRecord.
 */
export async function getMultipartSession(
  uploadId: string
): Promise<MultipartSessionResponse> {
  try {
    const response = await apiClient.get<ApiResponse<MultipartSessionResponse>>(
      API_ROUTES.UPLOADS.MULTIPART_SESSION(uploadId)
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to get multipart session");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}
