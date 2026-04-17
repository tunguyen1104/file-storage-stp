export type TenantUser = {
  id: string;
  name: string;
  email: string;
  role: "TENANT_ADMIN" | "MEMBER";
  lastLogin: string;
};
