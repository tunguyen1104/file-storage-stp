import { apiClient } from "@/lib/api-client";
import type { ApiResponse } from "@/types/api.types";
import { ApiError } from "@/types/api.types";

/**
 * Default SWR fetcher that wraps the centralized apiClient.
 * Unwraps the ApiResponse envelope and throws ApiError on failure.
 */
export async function swrFetcher<T>(url: string): Promise<T> {
  const response = await apiClient.get<ApiResponse<T>>(url);
  const body = response.data;

  if (!body.success) {
    const message =
      typeof body.errors === "string"
        ? body.errors
        : body.message ?? "Request failed";
    throw new ApiError(message, response.status, body.code, body.errors);
  }

  return body.data as T;
}
