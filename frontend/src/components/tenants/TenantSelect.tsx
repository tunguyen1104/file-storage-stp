"use client";

import { useTenantOptions } from "@/hooks/useTenantOptions";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";

interface TenantSelectProps {
  value?: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  placeholder?: string;
}

export function TenantSelect({
  value,
  onChange,
  disabled,
  placeholder = "Select a tenant",
}: TenantSelectProps) {
  const { options, isLoading, error } = useTenantOptions();

  if (isLoading) {
    return <Skeleton className="h-10 w-full" />;
  }

  if (error) {
    return (
      <div className="flex h-10 items-center rounded-md border border-destructive px-3 text-sm text-destructive">
        Failed to load tenants
      </div>
    );
  }

  return (
    <Select value={value || ""} onValueChange={onChange} disabled={disabled}>
      <SelectTrigger>
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        {options.map((opt) => (
          <SelectItem key={opt.value} value={opt.value}>
            {opt.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
