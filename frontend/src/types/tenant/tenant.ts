export type TenantResponse = {
  id: string;
  name: string;
  slug: string;
  isActive: boolean;
  storageQuotaBytes: number;
  storageUsedBytes: number;
  createdBy: string | null;
  createdAt: string;
  updatedAt: string | null;
};

/** Lightweight tenant item returned by GET /tenants/options (used for dropdowns) */
export type TenantOptionResponse = {
  id: string;
  name: string;
};

export type CreateTenantRequest = {
  name: string;
  slug: string;
  storageQuotaBytes?: string;
};

export type UpdateTenantRequest = {
  name?: string;
  slug?: string;
  storageQuotaBytes?: string;
};

export type ListTenantsParams = {
  isActive?: boolean;
  search?: string;
  page?: number;
  limit?: number;
};
