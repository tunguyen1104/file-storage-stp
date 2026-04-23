// src/hooks/useTenantOptions.ts
import useSWR from "swr";
import { listTenantOptions } from "@/lib/api/tenants";
import { API_ROUTES } from "@/lib/constants";

export type TenantOption = {
  label: string;
  value: string;
};

export function useTenantOptions(): {
  options: TenantOption[];
  isLoading: boolean;
  error: Error | undefined;
} {
  const { data, isLoading, error } = useSWR<TenantOption[]>(
    API_ROUTES.TENANTS.OPTIONS,
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
