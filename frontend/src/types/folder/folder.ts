export type FolderItemType = "folder" | "file";

export type FolderItem = {
  id: string;
  type: FolderItemType;
  name: string;
  parentId: string | null;
  sizeBytes: number;
  mimeType: string | null;
  createdAt: string;
};

export type FolderBreadcrumb = {
  id: string;
  name: string;
};

export type FolderMetadataResponse = {
  id: string;
  name: string;
  parentId: string | null;
  sizeBytes: number;
  tenantId: string;
  ownerId: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  breadcrumb?: FolderBreadcrumb[];
};

export type ListFolderParams = {
  page?: number;
  size?: number;
  sort?: "NAME" | "CREATED_AT" | "SIZE_BYTES";
  sortOrder?: "ASC" | "DESC";
  type?: "FOLDER" | "FILE";
  search?: string;
};

/** Response shape returned by GET /folders/root and GET /folders/{id}/children */
export type FolderPageResponse = {
  data: FolderItem[];
  pagination: {
    page: number;
    size: number;
    total: number;
    totalPages: number;
  };
};
