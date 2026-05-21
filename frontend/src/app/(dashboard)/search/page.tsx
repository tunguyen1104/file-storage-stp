"use client";

import { Suspense, useCallback, useEffect, useMemo } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import {
  AlertCircle,
  ArrowDownAZ,
  ArrowUpAZ,
  CornerDownLeft,
  RefreshCw,
  Search as SearchIcon,
  SearchX,
  X,
} from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { MimeFilterSidebar } from "@/components/search/MimeFilterSidebar";
import { SearchResultRow } from "@/components/search/SearchResultRow";
import { mimeLabel } from "@/components/search/search-helpers";
import { useSearchInput } from "@/contexts/search-input-context";
import { useSearch } from "@/hooks/use-search";
import { cn } from "@/lib/utils";
import type {
  SearchParams,
  SearchSortBy,
  SearchType,
  SortDirection,
} from "@/types/search";

const DEFAULT_SIZE = 20;
const MAX_PAGE_LINKS = 7;

const TYPE_VALUES: SearchType[] = ["ALL", "FILE", "FOLDER"];
const SORT_VALUES: SearchSortBy[] = ["SCORE", "NAME", "CREATED_AT"];
const SORT_LABEL: Record<SearchSortBy, string> = {
  SCORE: "Liên quan",
  NAME: "Tên",
  CREATED_AT: "Ngày tạo",
};
const TYPE_LABEL: Record<SearchType, string> = {
  ALL: "Tất cả",
  FILE: "Tệp tin",
  FOLDER: "Thư mục",
};

function parseType(raw: string | null): SearchType {
  return TYPE_VALUES.includes(raw as SearchType)
    ? (raw as SearchType)
    : "ALL";
}

function parseSort(raw: string | null): SearchSortBy {
  return SORT_VALUES.includes(raw as SearchSortBy)
    ? (raw as SearchSortBy)
    : "SCORE";
}

function parseSortDir(raw: string | null): SortDirection {
  return raw === "ASC" ? "ASC" : "DESC";
}

function parsePage(raw: string | null): number {
  const n = Number(raw);
  return Number.isFinite(n) && n >= 1 ? Math.floor(n) : 1;
}

function dateInputToIso(
  v: string | null,
  bound: "from" | "to"
): string | undefined {
  if (!v) return undefined;
  const m = v.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!m) return undefined;
  const time = bound === "from" ? "T00:00:00.000Z" : "T23:59:59.999Z";
  return `${v}${time}`;
}

function SearchPageInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { draft } = useSearchInput();

  // ── URL state ──
  const q = searchParams.get("q") ?? "";

  // True while the user is editing the navbar input but hasn't committed
  // (via Enter) yet. We use this to grey out filter controls so the user
  // can see the displayed filters belong to the previous query.
  const isDraftDirty = draft.trim() !== q.trim();
  const type = parseType(searchParams.get("type"));
  const mimeTypes = useMemo(
    () => searchParams.getAll("mimeType").filter(Boolean),
    [searchParams]
  );
  const dateFromInput = searchParams.get("dateFrom") ?? "";
  const dateToInput = searchParams.get("dateTo") ?? "";
  const page = parsePage(searchParams.get("page"));
  const sort = parseSort(searchParams.get("sort"));
  const sortDir = parseSortDir(searchParams.get("sortDir"));

  // The navbar SearchBar owns the query input on /search — it reads `q` from
  // the URL and writes it back on debounce. No local input state lives here.

  // ── Build the effective params and fire the search ──
  const apiParams: SearchParams | null = useMemo(() => {
    if (!q.trim()) return null;
    return {
      q: q.trim(),
      type,
      mimeType: type === "FOLDER" ? undefined : mimeTypes.length ? mimeTypes : undefined,
      dateFrom: dateInputToIso(dateFromInput, "from"),
      dateTo: dateInputToIso(dateToInput, "to"),
      page,
      size: DEFAULT_SIZE,
      sort,
      sortDir,
    };
  }, [q, type, mimeTypes, dateFromInput, dateToInput, page, sort, sortDir]);

  const { data, isLoading, error, mutate } = useSearch(apiParams);

  // Surface non-auth errors as toasts; the inline banner handles re-rendering.
  useEffect(() => {
    if (!error) return;
    if (error.status === 401) return; // axios interceptor handles redirect.
    toast.error(error.message || "Tìm kiếm thất bại");
  }, [error]);

  // ── URL mutation helpers ──
  const updateParams = useCallback(
    (mutator: (next: URLSearchParams) => void, resetPage = true) => {
      const next = new URLSearchParams(searchParams.toString());
      mutator(next);
      if (resetPage) next.delete("page");
      router.replace(`/search?${next.toString()}`);
    },
    [router, searchParams]
  );

  const handleTypeChange = useCallback(
    (value: SearchType) => {
      updateParams((next) => {
        if (value === "ALL") next.delete("type");
        else next.set("type", value);
        if (value === "FOLDER") next.delete("mimeType");
      });
    },
    [updateParams]
  );

  const handleSortChange = useCallback(
    (value: SearchSortBy) => {
      updateParams((next) => {
        if (value === "SCORE") next.delete("sort");
        else next.set("sort", value);
      });
    },
    [updateParams]
  );

  const handleSortDirToggle = useCallback(() => {
    if (sort === "SCORE") return; // ignored when sort=SCORE (spec).
    updateParams((next) => {
      const flipped: SortDirection = sortDir === "ASC" ? "DESC" : "ASC";
      if (flipped === "DESC") next.delete("sortDir");
      else next.set("sortDir", "ASC");
    });
  }, [sort, sortDir, updateParams]);

  const toggleMime = useCallback(
    (mime: string) => {
      updateParams((next) => {
        const current = next.getAll("mimeType");
        next.delete("mimeType");
        if (current.includes(mime)) {
          for (const m of current) if (m !== mime) next.append("mimeType", m);
        } else {
          for (const m of current) next.append("mimeType", m);
          next.append("mimeType", mime);
        }
      });
    },
    [updateParams]
  );

  const clearMimeFilter = useCallback(() => {
    updateParams((next) => next.delete("mimeType"));
  }, [updateParams]);

  const handleDateChange = useCallback(
    (which: "dateFrom" | "dateTo", value: string) => {
      updateParams((next) => {
        if (value) next.set(which, value);
        else next.delete(which);
      });
    },
    [updateParams]
  );

  const clearAllFilters = useCallback(() => {
    updateParams((next) => {
      next.delete("type");
      next.delete("mimeType");
      next.delete("dateFrom");
      next.delete("dateTo");
    });
  }, [updateParams]);

  const changePage = useCallback(
    (next: number) => {
      updateParams((nextParams) => {
        if (next <= 1) nextParams.delete("page");
        else nextParams.set("page", String(next));
      }, false);
    },
    [updateParams]
  );

  // ── Derived ──
  const totalPages = data ? Math.max(1, Math.ceil(data.totalHits / data.size)) : 1;
  const aggregations = data?.aggregations.mimeTypeCounts ?? {};
  const showSidebar = type !== "FOLDER";
  const hasActiveFilters =
    type !== "ALL" ||
    mimeTypes.length > 0 ||
    !!dateFromInput ||
    !!dateToInput;

  return (
    <div className="mx-auto w-full max-w-6xl space-y-4">
      {/* ── Filters card (query input lives in the navbar SearchBar) ── */}
      <div
        className={cn(
          "rounded-xl border border-gray-200 bg-white p-4 shadow-sm",
          isDraftDirty && "opacity-70"
        )}
      >
        {/* ── Filter row ── */}
        <fieldset
          disabled={isDraftDirty}
          aria-disabled={isDraftDirty}
          className="contents disabled:cursor-not-allowed"
        >
          <div className="flex flex-wrap items-center gap-2">
            <Select
              value={type}
              onValueChange={(v) => handleTypeChange(v as SearchType)}
              disabled={isDraftDirty}
            >
              <SelectTrigger size="sm" className="h-8 text-xs">
                <SelectValue placeholder="Loại" />
              </SelectTrigger>
              <SelectContent>
                {TYPE_VALUES.map((v) => (
                  <SelectItem key={v} value={v}>
                    {TYPE_LABEL[v]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Select
              value={sort}
              onValueChange={(v) => handleSortChange(v as SearchSortBy)}
              disabled={isDraftDirty}
            >
              <SelectTrigger size="sm" className="h-8 text-xs">
                <SelectValue placeholder="Sắp xếp" />
              </SelectTrigger>
              <SelectContent>
                {SORT_VALUES.map((v) => (
                  <SelectItem key={v} value={v}>
                    {SORT_LABEL[v]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Button
              type="button"
              variant="outline"
              size="sm"
              className="h-8 gap-1 text-xs"
              onClick={handleSortDirToggle}
              disabled={isDraftDirty || sort === "SCORE"}
              title={
                sort === "SCORE"
                  ? "Sắp xếp theo độ liên quan luôn giảm dần"
                  : sortDir === "ASC"
                    ? "Tăng dần"
                    : "Giảm dần"
              }
            >
              {sortDir === "ASC" ? (
                <ArrowUpAZ className="h-3.5 w-3.5" />
              ) : (
                <ArrowDownAZ className="h-3.5 w-3.5" />
              )}
              {sortDir === "ASC" ? "Tăng" : "Giảm"}
            </Button>

            <div className="flex items-center gap-1 text-xs text-gray-500">
              <span>Từ</span>
              <Input
                type="date"
                value={dateFromInput}
                onChange={(e) => handleDateChange("dateFrom", e.target.value)}
                className="h-8 w-[140px] border-gray-200 bg-gray-50 px-2 text-xs"
                disabled={isDraftDirty}
              />
              <span>đến</span>
              <Input
                type="date"
                value={dateToInput}
                onChange={(e) => handleDateChange("dateTo", e.target.value)}
                className="h-8 w-[140px] border-gray-200 bg-gray-50 px-2 text-xs"
                disabled={isDraftDirty}
              />
            </div>

            {hasActiveFilters && (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="h-8 gap-1 text-xs text-gray-500 hover:text-gray-700"
                onClick={clearAllFilters}
                disabled={isDraftDirty}
              >
                <X className="h-3.5 w-3.5" />
                Xóa bộ lọc
              </Button>
            )}
          </div>

          {/* ── Active filter chips ── */}
          {hasActiveFilters && (
            <div className="mt-3 flex flex-wrap gap-1.5">
              {type !== "ALL" && (
                <FilterChip
                  label={`Loại: ${TYPE_LABEL[type]}`}
                  onRemove={() => handleTypeChange("ALL")}
                  disabled={isDraftDirty}
                />
              )}
              {mimeTypes.map((m) => (
                <FilterChip
                  key={m}
                  label={mimeLabel(m)}
                  onRemove={() => toggleMime(m)}
                  disabled={isDraftDirty}
                />
              ))}
              {dateFromInput && (
                <FilterChip
                  label={`Từ ${dateFromInput}`}
                  onRemove={() => handleDateChange("dateFrom", "")}
                  disabled={isDraftDirty}
                />
              )}
              {dateToInput && (
                <FilterChip
                  label={`Đến ${dateToInput}`}
                  onRemove={() => handleDateChange("dateTo", "")}
                  disabled={isDraftDirty}
                />
              )}
            </div>
          )}
        </fieldset>

        {/* ── Out-of-sync hint ── */}
        {isDraftDirty && (
          <div className="mt-3 flex items-center gap-2 rounded-md border border-amber-100 bg-amber-50 px-3 py-2 text-xs text-amber-800">
            <CornerDownLeft className="h-3.5 w-3.5 shrink-0" />
            <span className="flex-1">
              Nhấn{" "}
              <kbd className="rounded border border-amber-200 bg-white px-1.5 py-0.5 text-[10px] font-medium">
                Enter
              </kbd>{" "}
              để áp dụng tìm kiếm mới. Bộ lọc và kết quả bên dưới đang phản ánh
              truy vấn cũ.
            </span>
          </div>
        )}
      </div>

      {/* ── Body ── */}
      {!q.trim() ? (
        <EmptyHero />
      ) : (
        <div className={cn("flex flex-col gap-4", showSidebar && "lg:flex-row")}>
          {showSidebar && (
            <MimeFilterSidebar
              counts={aggregations}
              selected={mimeTypes}
              onToggle={toggleMime}
              onClear={clearMimeFilter}
              disabled={isDraftDirty}
            />
          )}

          <div className="flex-1 space-y-3 min-w-0">
            {error && !isLoading && (
              <div className="flex items-center gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span className="flex-1">
                  {error.message || "Không thể tải kết quả tìm kiếm"}
                </span>
                <Button
                  size="sm"
                  variant="outline"
                  className="gap-1.5 border-red-200 text-red-600 hover:text-red-700"
                  onClick={() => mutate()}
                >
                  <RefreshCw className="h-3.5 w-3.5" />
                  Thử lại
                </Button>
              </div>
            )}

            {!error && (
              <div className="flex items-center justify-between text-xs text-gray-500">
                <div>
                  {isLoading && !data
                    ? "Đang tìm..."
                    : data
                      ? `${data.totalHits.toLocaleString("vi-VN")} kết quả`
                      : null}
                </div>
              </div>
            )}

            {isLoading && !data ? (
              <ResultsSkeleton />
            ) : data && data.items.length === 0 ? (
              <NoResults
                q={q}
                hasFilters={hasActiveFilters}
                onClearFilters={clearAllFilters}
              />
            ) : data ? (
              <ul className="space-y-2">
                {data.items.map((hit) => (
                  <li key={`${hit.type}-${hit.id}`}>
                    <SearchResultRow hit={hit} />
                  </li>
                ))}
              </ul>
            ) : null}

            {data && data.totalHits > data.size && (
              <PagerControls
                page={data.page}
                totalPages={totalPages}
                onChange={changePage}
              />
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function FilterChip({
  label,
  onRemove,
  disabled = false,
}: {
  label: string;
  onRemove: () => void;
  disabled?: boolean;
}) {
  return (
    <Badge
      variant="secondary"
      className="gap-1 pl-2 pr-1 text-xs font-normal"
    >
      <span className="max-w-[160px] truncate">{label}</span>
      <button
        type="button"
        onClick={onRemove}
        disabled={disabled}
        className="rounded-sm p-0.5 hover:bg-gray-200 disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:bg-transparent"
        aria-label={`Xóa lọc ${label}`}
      >
        <X className="h-3 w-3" />
      </button>
    </Badge>
  );
}

function ResultsSkeleton() {
  return (
    <ul className="space-y-2">
      {Array.from({ length: 5 }).map((_, i) => (
        <li
          key={i}
          className="rounded-xl border border-gray-100 bg-white p-4 space-y-2"
        >
          <div className="flex items-center gap-3">
            <div className="h-6 w-6 animate-pulse rounded bg-gray-100" />
            <div className="h-4 flex-1 max-w-md animate-pulse rounded bg-gray-100" />
          </div>
          <div className="h-3 w-2/3 animate-pulse rounded bg-gray-100" />
        </li>
      ))}
    </ul>
  );
}

function EmptyHero() {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-gray-200 bg-white py-16 text-center">
      <SearchIcon className="h-10 w-10 text-gray-300" />
      <div>
        <p className="text-sm font-medium text-gray-600">
          Nhập từ khóa để bắt đầu tìm kiếm
        </p>
        <p className="mt-1 text-xs text-gray-400">
          Tìm tệp tin và thư mục theo tên hoặc nội dung.
        </p>
      </div>
    </div>
  );
}

function NoResults({
  q,
  hasFilters,
  onClearFilters,
}: {
  q: string;
  hasFilters: boolean;
  onClearFilters: () => void;
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-gray-200 bg-white py-16 text-center">
      <SearchX className="h-10 w-10 text-gray-300" />
      <div>
        <p className="text-sm font-medium text-gray-600">
          Không tìm thấy kết quả cho “{q}”
        </p>
        <p className="mt-1 text-xs text-gray-400">
          Thử dùng từ khóa khác hoặc gỡ bớt bộ lọc.
        </p>
      </div>
      {hasFilters && (
        <Button
          variant="outline"
          size="sm"
          className="gap-1.5"
          onClick={onClearFilters}
        >
          <X className="h-3.5 w-3.5" />
          Xóa bộ lọc
        </Button>
      )}
    </div>
  );
}

function buildPagerWindow(current: number, total: number): (number | "…")[] {
  if (total <= MAX_PAGE_LINKS) {
    return Array.from({ length: total }, (_, i) => i + 1);
  }
  const out: (number | "…")[] = [1];
  const start = Math.max(2, current - 2);
  const end = Math.min(total - 1, current + 2);
  if (start > 2) out.push("…");
  for (let p = start; p <= end; p++) out.push(p);
  if (end < total - 1) out.push("…");
  out.push(total);
  return out;
}

function PagerControls({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (next: number) => void;
}) {
  const window = buildPagerWindow(page, totalPages);
  return (
    <Pagination className="pt-2">
      <PaginationContent>
        <PaginationItem>
          <PaginationPrevious
            href="#"
            onClick={(e) => {
              e.preventDefault();
              if (page > 1) onChange(page - 1);
            }}
            className={page === 1 ? "pointer-events-none opacity-50" : ""}
          />
        </PaginationItem>
        {window.map((p, idx) =>
          p === "…" ? (
            <PaginationItem key={`e-${idx}`}>
              <PaginationEllipsis />
            </PaginationItem>
          ) : (
            <PaginationItem key={p}>
              <PaginationLink
                href="#"
                isActive={p === page}
                onClick={(e) => {
                  e.preventDefault();
                  onChange(p);
                }}
              >
                {p}
              </PaginationLink>
            </PaginationItem>
          )
        )}
        <PaginationItem>
          <PaginationNext
            href="#"
            onClick={(e) => {
              e.preventDefault();
              if (page < totalPages) onChange(page + 1);
            }}
            className={
              page === totalPages ? "pointer-events-none opacity-50" : ""
            }
          />
        </PaginationItem>
      </PaginationContent>
    </Pagination>
  );
}

export default function SearchPage() {
  return (
    <Suspense
      fallback={
        <div className="flex h-full items-center justify-center">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-[#7C3AED] border-t-transparent" />
        </div>
      }
    >
      <SearchPageInner />
    </Suspense>
  );
}
