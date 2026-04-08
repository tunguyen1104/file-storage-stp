"use client";

import { SWRConfiguration } from "swr";
import { swrFetcher } from "@/lib/swr-fetcher";

export const defaultSWRConfig: SWRConfiguration = {
  fetcher: swrFetcher,
  revalidateOnFocus: false,
  revalidateOnReconnect: true,
  shouldRetryOnError: true,
  errorRetryCount: 2,
  dedupingInterval: 5000,
  keepPreviousData: true,
};
