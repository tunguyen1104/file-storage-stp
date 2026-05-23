import useSWR from "swr";
import {
  getAuditLogStats,
  listAuditLogs,
  searchUsers,
} from "@/lib/api/audit";
import { listTenantOptions } from "@/lib/api/tenants";
import type {
  AuditLogEntry,
  AuditLogStats,
  ListAuditLogsParams,
  UserSearchResult,
} from "@/types/audit/audit";
import type { PageResponse } from "@/types/api.types";
import type { TenantOptionResponse } from "@/types/tenant/tenant";
import type { AppError } from "@/lib/api/core/api-error";

export function useAuditLogs(params: ListAuditLogsParams) {
  const key = ["audit-logs", params] as const;
  return useSWR<PageResponse<AuditLogEntry>, AppError>(key, () =>
    listAuditLogs(params)
  );
}

export function useAuditLogStats(enabled = true) {
  return useSWR<AuditLogStats, AppError>(
    enabled ? "audit-log-stats" : null,
    () => getAuditLogStats()
  );
}

export function useTenantOptionsList(enabled = true) {
  return useSWR<TenantOptionResponse[], AppError>(
    enabled ? "tenant-options" : null,
    () => listTenantOptions(),
    {
      revalidateOnFocus: false,
      revalidateIfStale: false,
    }
  );
}

export function useUserSearch(email: string, tenantId?: string) {
  const trimmed = email.trim();
  const enabled = trimmed.length >= 2;
  return useSWR<UserSearchResult[], AppError>(
    enabled ? ["users-search", trimmed.toLowerCase(), tenantId ?? null] : null,
    () => searchUsers({ email: trimmed, tenantId })
  );
}
