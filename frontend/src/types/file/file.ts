import type { FileStatus, FileSortBy, SortDirection } from "@/lib/constants";

/** Lightweight user reference returned inside file responses */
export type UserSummaryResponse = {
  id: string;
  fullName: string;
};

/**
 * Full file entity returned by upload, restore, and delete endpoints.
 * Includes storage internals like `storageKey`.
 */
export type FileResponse = {
  id: string;
  originalName: string;
  mimeType: string;
  sizeBytes: number;
  status: FileStatus;
  folderId: string | null;
  tenantId: string | null;
  storageKey: string;
  checksumMd5: string | null;
  thumbnailKey: string | null;
  isIndexed: boolean;
  uploadedBy: UserSummaryResponse;
  deletedAt: string | null;
  scheduledDeleteAt: string | null;
  createdAt: string;
  updatedAt: string;
};

/**
 * File metadata entity (without `storageKey`) returned by list and
 * detail endpoints.
 */
export type FileMetadataResponse = {
  id: string;
  originalName: string;
  mimeType: string;
  sizeBytes: number;
  status: FileStatus;
  folderId: string | null;
  tenantId: string | null;
  checksumMd5: string | null;
  thumbnailKey: string | null;
  isIndexed: boolean;
  uploadedBy: UserSummaryResponse;
  deletedAt: string | null;
  scheduledDeleteAt: string | null;
  createdAt: string;
  updatedAt: string;
};

/** Presigned GET URL for previewing / viewing a file */
export type PresignedGetUrlResponse = {
  getUrl: string;
  expiresAt: string;
  originalName: string;
  mimeType: string;
  sizeBytes: number;
};

/** Presigned download URL with Content-Disposition=attachment */
export type PresignedDownloadResponse = {
  downloadUrl: string;
  expiresAt: string;
  originalName: string;
  mimeType: string;
  sizeBytes: number;
};

/** Query params for GET /api/v1/files */
export type ListFilesParams = {
  folderId?: string;
  status?: FileStatus;
  uploadedBy?: string;
  tenantId?: string;
  search?: string;
  page?: number;
  limit?: number;
  sortBy?: FileSortBy;
  sortDirection?: SortDirection;
};
