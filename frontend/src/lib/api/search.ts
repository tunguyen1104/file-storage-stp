import { apiClient } from "@/lib/api-client";
import { API_ROUTES } from "@/lib/constants";
import { toAppError } from "@/lib/api/core/api-error";
import type { ApiResponse } from "@/types/api.types";
import type {
  SearchParams,
  SearchResponse,
  SuggestParams,
  SuggestResponse,
} from "@/types/search";

const SUGGEST_ROUTE = `${API_ROUTES.SEARCH}/suggest`;

function buildSuggestQuery(params: SuggestParams): URLSearchParams {
  const query = new URLSearchParams();
  query.set("q", params.q);
  if (params.type) query.set("type", params.type);
  if (params.size != null) query.set("size", String(params.size));
  return query;
}

function buildSearchQuery(params: SearchParams): URLSearchParams {
  const query = new URLSearchParams();
  query.set("q", params.q);
  if (params.type) query.set("type", params.type);
  if (params.mimeType?.length) {
    for (const m of params.mimeType) query.append("mimeType", m);
  }
  if (params.dateFrom) query.set("dateFrom", params.dateFrom);
  if (params.dateTo) query.set("dateTo", params.dateTo);
  if (params.page != null) query.set("page", String(params.page));
  if (params.size != null) query.set("size", String(params.size));
  if (params.sort) query.set("sort", params.sort);
  if (params.sortDir) query.set("sortDir", params.sortDir);
  return query;
}

export async function fetchSuggest(
  params: SuggestParams,
  signal?: AbortSignal
): Promise<SuggestResponse> {
  try {
    const response = await apiClient.get<ApiResponse<SuggestResponse>>(
      SUGGEST_ROUTE,
      { params: buildSuggestQuery(params), signal }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to fetch suggestions");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}

export async function fetchSearch(
  params: SearchParams,
  signal?: AbortSignal
): Promise<SearchResponse> {
  try {
    const response = await apiClient.get<ApiResponse<SearchResponse>>(
      API_ROUTES.SEARCH,
      { params: buildSearchQuery(params), signal }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message ?? "Failed to fetch search results");
    }
    return response.data.data;
  } catch (error) {
    throw toAppError(error);
  }
}
