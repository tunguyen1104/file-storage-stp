import { apiClient } from "@/lib/api-client";
import type { ApiResponse } from "@/types/api.types";
import { ApiError } from "@/types/api.types";

/**
 * Default SWR fetcher that wraps the centralized apiClient.
 * Unwraps the ApiResponse envelope and throws ApiError on failure.
 */
export async function swrFetcher<T>(url: string): Promise<T> {
  const response = await apiClient.get<ApiResponse<T>>(url);

  if (response.data.status === "ERROR") {
    throw new ApiError(
      response.data.message ?? "API Logic Error",
      response.status,
      response.data.errorCode,
      response.data.errors
    );
  }

  return response.data.data as T;
}
