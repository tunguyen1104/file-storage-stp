"use client";

import { useMemo, useState } from "react";
import { Building2, Check, ChevronsUpDown, Search } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { useTenantOptionsList } from "@/hooks/use-audit-logs";

type Props = {
  value?: string;
  onChange: (id: string | undefined) => void;
};

export function TenantFilterDropdown({ value, onChange }: Props) {
  const { data, isLoading } = useTenantOptionsList(true);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");

  const tenants = useMemo(() => data ?? [], [data]);

  const sorted = useMemo(() => {
    return [...tenants].sort((a, b) =>
      a.name.localeCompare(b.name, "vi", { sensitivity: "base" })
    );
  }, [tenants]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return sorted;
    return sorted.filter(
      (t) =>
        t.name.toLowerCase().includes(q) || t.slug.toLowerCase().includes(q)
    );
  }, [sorted, query]);

  const selected = tenants.find((t) => t.id === value);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className="h-10 w-full justify-between gap-2 font-normal"
        >
          <span className="flex min-w-0 items-center gap-2">
            <Building2 className="h-4 w-4 shrink-0 text-gray-500" />
            <span className="truncate">
              {selected ? (
                <>
                  {selected.name}{" "}
                  <span className="text-gray-400">({selected.slug})</span>
                </>
              ) : (
                <span className="text-gray-500">Tất cả tổ chức</span>
              )}
            </span>
          </span>
          <ChevronsUpDown className="h-4 w-4 shrink-0 text-gray-400" />
        </Button>
      </PopoverTrigger>

      <PopoverContent
        align="start"
        className="w-[--radix-popover-trigger-width] min-w-[280px] p-0"
      >
        <div className="border-b border-gray-100 p-2">
          <div className="relative">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
            <Input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Tìm tổ chức..."
              className="h-9 pl-8"
              autoFocus
            />
          </div>
        </div>

        <ul className="max-h-64 overflow-y-auto py-1">
          <li>
            <button
              type="button"
              onClick={() => {
                onChange(undefined);
                setOpen(false);
              }}
              className={cn(
                "flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm hover:bg-violet-50",
                !value && "bg-violet-50 font-medium text-violet-700"
              )}
            >
              <span>Tất cả tổ chức</span>
              {!value && <Check className="h-4 w-4 text-violet-700" />}
            </button>
          </li>

          {isLoading && (
            <li className="px-3 py-2">
              <Skeleton className="h-4 w-3/4" />
            </li>
          )}

          {!isLoading && filtered.length === 0 && query && (
            <li className="px-3 py-3 text-sm text-gray-500">
              Không tìm thấy tổ chức nào.
            </li>
          )}

          {filtered.map((t) => (
            <li key={t.id}>
              <button
                type="button"
                onClick={() => {
                  onChange(t.id);
                  setOpen(false);
                }}
                className={cn(
                  "flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm hover:bg-violet-50",
                  value === t.id && "bg-violet-50 font-medium text-violet-700"
                )}
              >
                <span className="flex min-w-0 flex-col">
                  <span className="truncate">{t.name}</span>
                  <span className="truncate text-[11px] text-gray-500">
                    {t.slug}
                  </span>
                </span>
                {value === t.id && (
                  <Check className="h-4 w-4 shrink-0 text-violet-700" />
                )}
              </button>
            </li>
          ))}
        </ul>
      </PopoverContent>
    </Popover>
  );
}
