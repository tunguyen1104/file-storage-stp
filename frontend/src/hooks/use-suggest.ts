"use client";

import { useEffect, useState } from "react";
import { fetchSuggest } from "@/lib/api/search";
import { AppError } from "@/lib/api/core/api-error";
import type {
  SearchType,
  SuggestHitItem,
} from "@/types/search";

type UseSuggestState = {
  items: SuggestHitItem[];
  totalHits: number;
  isLoading: boolean;
  error: AppError | null;
};

type UseSuggestOptions = {
  /** Restrict the result type. Defaults to ALL. */
  type?: SearchType;
  /** Cap on items the dropdown wants to render (1-10). Default 8. */
  size?: number;
};

const EMPTY_STATE: UseSuggestState = {
  items: [],
  totalHits: 0,
  isLoading: false,
  error: null,
};

/**
 * Search-as-you-type hook. Pass an already-debounced query string.
 * Cancels in-flight requests when the query changes so the dropdown
 * never displays results for a stale prefix.
 */
export function useSuggest(
  query: string,
  options?: UseSuggestOptions
): UseSuggestState {
  const trimmed = query.trim();
  const { type, size } = options ?? {};
  const [state, setState] = useState<UseSuggestState>(EMPTY_STATE);

  useEffect(() => {
    if (trimmed.length < 1) {
      setState(EMPTY_STATE);
      return;
    }

    const controller = new AbortController();
    setState((prev) => ({ ...prev, isLoading: true, error: null }));

    fetchSuggest({ q: trimmed, type, size }, controller.signal)
      .then((data) => {
        setState({
          items: data.items,
          totalHits: data.totalHits,
          isLoading: false,
          error: null,
        });
      })
      .catch((err: unknown) => {
        // Cancellation is expected behaviour, not an error to surface.
        if (controller.signal.aborted) return;
        const appErr =
          err instanceof AppError
            ? err
            : new AppError(
                err instanceof Error ? err.message : "Suggest request failed"
              );
        setState({ items: [], totalHits: 0, isLoading: false, error: appErr });
      });

    return () => controller.abort();
  }, [trimmed, type, size]);

  return state;
}
