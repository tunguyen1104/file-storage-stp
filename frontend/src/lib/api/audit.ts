import { apiClient } from "@/lib/api-client";
import { API_ROUTES } from "@/lib/constants";
import { toAppError } from "@/lib/api/core/api-error";
import type { ApiResponse, PageResponse } from "@/types/api.types";
import type {
  AuditLogEntry,
  AuditLogStats,
  ExportAuditLogsParams,
  ListAuditLogsParams,
  SearchUsersParams,
  UserSearchResult,
} from "@/types/audit/audit";

/**
 * Axios serializes arrays by repeating params by default, but to be explicit
 * about the `action` filter (which the backend accepts both repeated and
 * comma-separated), we serialize it ourselves into a `URLSearchParams`.
 */
function buildSearchParams(
  params?: Record<string, unknown>
): URLSearchParams | undefined {
  if (!params) return undefined;
  const sp = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === "") continue;
    if (Array.isArray(value)) {
      for (const item of value) {
        if (item === undefined || item === null || item === "") continue;
        sp.append(key, String(item));
      }
    } else {
      sp.append(key, String(value));
    }
  }
  return sp;
}

export async function listAuditLogs(
  params?: ListAuditLogsParams
): Promise<PageResponse<AuditLogEntry>> {
  try {
    const response = await apiClient.get<
      ApiResponse<PageResponse<AuditLogEntry>>
    >(API_ROUTES.AUDIT_LOGS.LIST, {
      params: buildSearchParams(params as unknown as Record<string, unknown>),
    });
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to fetch audit logs");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function getAuditLogStats(): Promise<AuditLogStats> {
  try {
    const response = await apiClient.get<ApiResponse<AuditLogStats>>(
      API_ROUTES.AUDIT_LOGS.STATS
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(
        response.data.message ?? "Failed to fetch audit log stats"
      );
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function searchUsers(
  params: SearchUsersParams
): Promise<UserSearchResult[]> {
  try {
    const response = await apiClient.get<ApiResponse<UserSearchResult[]>>(
      API_ROUTES.USERS_SEARCH,
      {
        params: buildSearchParams(params as unknown as Record<string, unknown>),
      }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to search users");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

/**
 * Build the URL the browser should navigate to in order to download the CSV.
 * The export endpoint streams `text/csv` with a `Content-Disposition: attachment`
 * header, so the simplest and most memory-efficient trigger is
 * `window.location.href = …`.
 */
export function buildAuditLogsExportUrl(
  params?: ExportAuditLogsParams
): string {
  const sp = buildSearchParams(
    params as unknown as Record<string, unknown>
  );
  const qs = sp && sp.toString().length > 0 ? `?${sp.toString()}` : "";
  return `${API_ROUTES.AUDIT_LOGS.EXPORT}${qs}`;
}
