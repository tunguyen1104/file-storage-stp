import { apiClient } from "@/lib/api-client";
import { AppError, toAppError } from "@/lib/api/core/api-error";
import type { ApiResponse } from "@/types/api.types";

/**
 * Default SWR fetcher that wraps the centralized apiClient.
 * Unwraps the ApiResponse envelope and throws AppError on failure.
 */
export async function swrFetcher<T>(url: string): Promise<T> {
  try {
    const response = await apiClient.get<ApiResponse<T>>(url);
    const body = response.data;

    if (!body.success || body.data === undefined) {
      throw new AppError(body.message ?? "Request failed");
    }

    return body.data;
  } catch (error) {
    throw toAppError(error);
  }
}
