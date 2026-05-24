// src/hooks/useTenantOptions.ts
import useSWR from "swr";
import { listTenantOptions } from "@/lib/api/tenants";
import { API_ROUTES } from "@/lib/constants";

export type TenantOption = {
  label: string;
  value: string;
};

export function useTenantOptions(enabled: boolean = true): {
  options: TenantOption[];
  isLoading: boolean;
  error: Error | undefined;
} {
  const { data, isLoading, error } = useSWR<TenantOption[]>(
    enabled ? API_ROUTES.TENANTS.OPTIONS : null,
    () =>
      listTenantOptions().then((items) =>
        items.map((t) => ({ label: t.name, value: t.id }))
      )
  );

  return {
    options: data ?? [],
    isLoading,
    error: error as Error | undefined,
  };
}
