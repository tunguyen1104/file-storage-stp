export type TenantUser = {
  id: string;
  name: string;
  email: string;
  role: "TENANT_ADMIN" | "MEMBER";
  lastLogin: string;
};

export type TenantItem = {
  id: string;
  name: string;
  slug: string;
  storageQuotaBytes?: number;
  isActive?: boolean;
};

export type ListTenantsParams = {
  isActive?: boolean;
  search?: string;
  page?: number;
  limit?: number;
};

export type CreateTenantRequest = {
  name: string;
  slug: string;
  storageQuotaBytes?: number;
};

export type UpdateTenantRequest = {
  name?: string;
  slug?: string;
  storageQuotaBytes?: number;
};
