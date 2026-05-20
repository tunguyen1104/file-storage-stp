export type ShareStatus = "ACTIVE" | "EXPIRED" | "REVOKED";
export type ShareRecipientType = "USER" | "TENANT";
export type PermissionFile = "VIEW" | "EDIT";
export type ShareRevokeScope = "GRANTED" | "RECEIVED" | "BOTH";

export interface UserSummary {
  id: string;
  email: string;
  fullName: string;
}

export interface TenantSummary {
  id: string;
  name: string;
}

export interface ShareResponse {
  id: string;
  tenantId: string;
  tenantName?: string;
  resourceType: "file" | "folder";
  fileId: string | null;
  folderId: string | null;
  resourceName?: string;
  permission: "VIEW" | "EDIT";
  status: ShareStatus;
  recipientType: ShareRecipientType;
  sharedWithUser: UserSummary | null;
  sharedWithTenant: TenantSummary | null;
  sharedBy: { id: string; fullName: string };
  expiresAt: string;
  createdAt: string;
  revokedAt: string | null;
}

export interface ShareResourceSummary {
  type: "file" | "folder";
  id: string;
  name: string;
  mimeType: string | null;
  sizeBytes: number | null;
}

export interface ShareWithResourceResponse extends Omit<ShareResponse, "tenantId"> {
  resource: ShareResourceSummary;
}

export interface SharedWithMeResponse {
  shareId: string;
  resourceType: "file" | "folder";
  permission: "VIEW" | "EDIT";
  status: ShareStatus;
  recipientType: ShareRecipientType;
  expiresAt: string;
  sharedBy: { id: string; fullName: string };
  resource: ShareResourceSummary;
}

export interface ShareExpirationResponse {
  id: string;
  status: ShareStatus;
  expiresAt: string;
  revokedAt: string | null;
  updatedAt: string;
}
