"use client";

import useSWR, { type SWRResponse } from "swr";
import { fetchSearch } from "@/lib/api/search";
import type { AppError } from "@/lib/api/core/api-error";
import type { SearchParams, SearchResponse } from "@/types/search";

type UseSearchResult = {
  data: SearchResponse | undefined;
  isLoading: boolean;
  error: AppError | undefined;
  mutate: SWRResponse<SearchResponse, AppError>["mutate"];
};

/**
 * Full-text search hook. Disable by passing an empty/blank `q` — in that case
 * SWR is told not to fetch and the result is undefined.
 *
 * Cache key is a structured array that includes every param so two different
 * queries / pages / filters never collide.
 */
export function useSearch(params: SearchParams | null): UseSearchResult {
  const enabled = !!params && params.q.trim().length > 0;

  const key = enabled
    ? ([
        "/api/v1/search",
        params!.q.trim(),
        params!.type ?? "ALL",
        params!.mimeType ? [...params!.mimeType].sort().join(",") : "",
        params!.dateFrom ?? "",
        params!.dateTo ?? "",
        params!.page ?? 1,
        params!.size ?? 20,
        params!.sort ?? "SCORE",
        params!.sortDir ?? "DESC",
      ] as const)
    : null;

  const swr = useSWR<SearchResponse, AppError, typeof key>(
    key,
    () => fetchSearch(params!),
    { revalidateOnFocus: false, keepPreviousData: true }
  );

  return {
    data: swr.data,
    isLoading: swr.isLoading,
    error: swr.error,
    mutate: swr.mutate,
  };
}
