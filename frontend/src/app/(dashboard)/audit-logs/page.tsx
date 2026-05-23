"use client";

import { useEffect, useMemo, useState } from "react";
import { History, RefreshCw, ShieldAlert } from "lucide-react";
import { useSWRConfig } from "swr";

import { Button } from "@/components/ui/button";
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
import { useAuth } from "@/hooks/use-auth";
import { ROLES } from "@/lib/constants";
import { cn } from "@/lib/utils";
import type {
  AuditLogEntry,
  AuditLogSortBy,
  AuditSortDirection,
  ListAuditLogsParams,
} from "@/types/audit/audit";
import {
  useAuditLogs,
  useTenantOptionsList,
} from "@/hooks/use-audit-logs";
import {
  AuditFilterPanel,
  EMPTY_FILTERS,
  computeDefaultRange,
  DEFAULT_RANGE_DAYS,
  type AuditFilters,
} from "@/components/audit-logs/AuditFilterPanel";
import { AuditStatsWidget } from "@/components/audit-logs/AuditStatsWidget";
import { AuditLogTable } from "@/components/audit-logs/AuditLogTable";
import { AuditLogDetailsSheet } from "@/components/audit-logs/AuditLogDetailsSheet";
import { AuditExportButton } from "@/components/audit-logs/AuditExportButton";

const PAGE_SIZE_OPTIONS = [20, 50, 100];

function makeInitialFilters(): AuditFilters {
  const { fromIso, toIso } = computeDefaultRange(DEFAULT_RANGE_DAYS);
  return {
    ...EMPTY_FILTERS,
    from: fromIso,
    to: toIso,
    quickRangeDays: DEFAULT_RANGE_DAYS,
  };
}

function countActiveFilters(filters: AuditFilters): number {
  let n = 0;
  if (filters.userEmail.trim()) n++;
  if (filters.actions.length > 0) n++;
  if (filters.fileId.trim()) n++;
  if (filters.tenantId) n++;
  if (filters.from) n++;
  if (filters.to) n++;
  if (filters.ipAddress.trim()) n++;
  if (filters.userAgent.trim()) n++;
  if (filters.severities.length > 0) n++;
  return n;
}

function filtersToParams(
  filters: AuditFilters,
  page: number,
  limit: number,
  sortBy: AuditLogSortBy,
  sortDir: AuditSortDirection
): ListAuditLogsParams {
  return {
    userEmail: filters.userEmail.trim() || undefined,
    action: filters.actions.length > 0 ? filters.actions : undefined,
    fileId: filters.fileId.trim() || undefined,
    tenantId: filters.tenantId,
    from: filters.from,
    to: filters.to,
    ipAddress: filters.ipAddress.trim() || undefined,
    userAgent: filters.userAgent.trim() || undefined,
    page,
    limit,
    sortBy,
    sortDir,
  };
}

export default function AuditLogsPage() {
  const { user } = useAuth();
  const isSuperAdmin = user?.role === ROLES.SUPER_ADMIN;
  const isTenantAdmin = user?.role === ROLES.TENANT_ADMIN;

  const [filters, setFilters] = useState<AuditFilters>(() =>
    makeInitialFilters()
  );
  const [page, setPage] = useState(1);
  const [limit, setLimit] = useState(20);
  const [sortBy, setSortBy] = useState<AuditLogSortBy>("CREATED_AT");
  const [sortDir, setSortDir] = useState<AuditSortDirection>("DESC");
  const [selectedEntry, setSelectedEntry] = useState<AuditLogEntry | null>(
    null
  );

  const params = useMemo(
    () => filtersToParams(filters, page, limit, sortBy, sortDir),
    [filters, page, limit, sortBy, sortDir]
  );
  const exportParams = useMemo(() => {
    const { page: _p, limit: _l, ...rest } = params;
    void _p;
    void _l;
    return rest;
  }, [params]);

  const { data, isLoading, isValidating, mutate, error } = useAuditLogs(params);
  const { mutate: globalMutate } = useSWRConfig();

  async function refreshAll() {
    // Stats endpoint không nhận filter — luôn gọi lại.
    const statsTask = globalMutate("audit-log-stats");

    // Nếu đang ở rolling window (quick-pick / default), dịch `to` về now.
    // setFilters sẽ làm params thay đổi → SWR tự fetch lại bằng key mới,
    // không cần gọi mutate() thủ công.
    if (filters.quickRangeDays != null) {
      const { fromIso, toIso } = computeDefaultRange(filters.quickRangeDays);
      setFilters({ ...filters, from: fromIso, to: toIso });
      await statsTask;
      return;
    }

    // Custom (frozen) window → giữ nguyên from/to, chỉ revalidate cache.
    await Promise.all([mutate(), statsTask]);
  }

  // Reset to page 1 whenever the filters/sort/pageSize change.
  useEffect(() => {
    setPage(1);
  }, [filters, limit, sortBy, sortDir]);

  // Reset page on a hard limit change (filters effect above also fires, but
  // this guards against any future divergence).

  const entries = useMemo(() => data?.data ?? [], [data]);
  const totalPages = data?.totalPages ?? 1;
  const totalElements = data?.totalElements ?? 0;

  // Client-side severity filter on the current page.
  const visibleEntries = useMemo(() => {
    if (filters.severities.length === 0) return entries;
    const set = new Set(filters.severities);
    return entries.filter((e) => set.has(e.severity));
  }, [entries, filters.severities]);

  // Tenant cache for column rendering (super_admin only).
  const { data: tenantOptions } = useTenantOptionsList(isSuperAdmin);
  const tenantNameById = useMemo(() => {
    const map: Record<string, { name: string; slug: string }> = {};
    for (const t of tenantOptions ?? []) {
      map[t.id] = { name: t.name, slug: t.slug };
    }
    return map;
  }, [tenantOptions]);

  const activeFilterCount = countActiveFilters(filters);
  const showFromTo =
    visibleEntries.length > 0
      ? `${(page - 1) * limit + 1}-${Math.min(
          (page - 1) * limit + visibleEntries.length,
          totalElements
        )}`
      : "0";

  // ── Role gate ───────────────────────────────────────────────────────────────
  if (user && user.role === ROLES.MEMBER) {
    return (
      <section className="p-6">
        <div className="mx-auto max-w-md rounded-xl border border-red-200 bg-red-50 p-8 text-center">
          <ShieldAlert className="mx-auto h-10 w-10 text-red-600" />
          <h1 className="mt-3 text-lg font-semibold text-red-900">
            Bạn không có quyền xem nhật ký hoạt động
          </h1>
          <p className="mt-1 text-sm text-red-700">
            Tính năng này chỉ dành cho quản trị viên.
          </p>
        </div>
      </section>
    );
  }

  return (
    <section className="space-y-6 p-4 md:p-6">
      {/* Header */}
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div className="flex items-start gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-violet-100 text-violet-700">
            <History className="h-5 w-5" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-gray-900">
              Nhật ký hoạt động
            </h1>
            <p className="text-sm text-gray-500">
              {isSuperAdmin
                ? "Tất cả sự kiện trên toàn bộ tổ chức."
                : "Sự kiện trong phạm vi tổ chức của bạn."}{" "}
              Bản ghi có thể trễ vài giây so với thao tác thực tế.
            </p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            className="h-10 gap-2"
            onClick={() => void refreshAll()}
            disabled={isValidating}
          >
            <RefreshCw
              className={cn(
                "h-4 w-4",
                isValidating && "animate-spin"
              )}
            />
            Làm mới
          </Button>
          <AuditExportButton
            params={exportParams}
            totalElements={totalElements}
            disabled={isLoading}
          />
        </div>
      </div>

      {/* Stats */}
      <AuditStatsWidget
        scopeLabel={isSuperAdmin ? "tất cả tổ chức" : "tổ chức của bạn"}
      />

      {/* Filters */}
      <AuditFilterPanel
        filters={filters}
        onChange={setFilters}
        onReset={() => setFilters(makeInitialFilters())}
        showTenantFilter={isSuperAdmin}
        activeFilterCount={activeFilterCount}
      />

      {/* Table */}
      <section className="rounded-xl border border-gray-200 bg-white shadow-sm">
        <header className="flex flex-col gap-2 px-6 py-4 border-b border-gray-100 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 className="text-base font-semibold text-gray-900">
              Sự kiện gần nhất
            </h2>
            <p className="text-xs text-gray-500">
              {isLoading
                ? "Đang tải..."
                : `Hiển thị ${showFromTo} trên tổng số ${totalElements.toLocaleString(
                    "vi-VN"
                  )} sự kiện`}
              {filters.severities.length > 0 &&
                visibleEntries.length !== entries.length && (
                  <> · Đã lọc mức độ ở trang này</>
                )}
            </p>
          </div>

          <div className="flex items-center gap-2">
            <label className="text-xs text-gray-500">Số dòng / trang</label>
            <Select
              value={String(limit)}
              onValueChange={(v) => setLimit(Number(v))}
            >
              <SelectTrigger className="h-9 w-[88px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {PAGE_SIZE_OPTIONS.map((n) => (
                  <SelectItem key={n} value={String(n)}>
                    {n}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </header>

        {error && (
          <div className="border-b border-red-200 bg-red-50 px-6 py-3 text-sm text-red-700">
            Không tải được nhật ký. Vui lòng thử lại.
          </div>
        )}

        <AuditLogTable
          entries={visibleEntries}
          isLoading={isLoading}
          showTenantColumn={isSuperAdmin}
          tenantNameById={tenantNameById}
          sortBy={sortBy}
          sortDir={sortDir}
          onSortChange={(by, dir) => {
            setSortBy(by);
            setSortDir(dir);
          }}
          onSelect={setSelectedEntry}
        />

        <footer className="flex flex-col gap-2 border-t border-gray-100 px-6 py-3 text-sm text-gray-500 md:flex-row md:items-center md:justify-between">
          <span>
            Trang {page} / {Math.max(1, totalPages)}
          </span>
          {totalPages > 1 && (
            <PageNav
              currentPage={page}
              totalPages={totalPages}
              onChange={setPage}
            />
          )}
        </footer>
      </section>

      <AuditLogDetailsSheet
        entry={selectedEntry}
        open={selectedEntry !== null}
        onOpenChange={(open) => {
          if (!open) setSelectedEntry(null);
        }}
      />

      {/* TENANT_ADMIN tenant filter must not be rendered — already enforced in AuditFilterPanel via showTenantFilter prop */}
      {isTenantAdmin && null}
    </section>
  );
}

function PageNav({
  currentPage,
  totalPages,
  onChange,
}: {
  currentPage: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  // Build a compact page list with ellipses.
  const pages: (number | "…")[] = [];
  const push = (v: number | "…") => pages.push(v);
  if (totalPages <= 7) {
    for (let i = 1; i <= totalPages; i++) push(i);
  } else {
    push(1);
    if (currentPage > 4) push("…");
    const start = Math.max(2, currentPage - 1);
    const end = Math.min(totalPages - 1, currentPage + 1);
    for (let i = start; i <= end; i++) push(i);
    if (currentPage < totalPages - 3) push("…");
    push(totalPages);
  }

  return (
    <Pagination className="mx-0 w-auto">
      <PaginationContent>
        <PaginationItem>
          <PaginationPrevious
            href="#"
            onClick={(e) => {
              e.preventDefault();
              if (currentPage > 1) onChange(currentPage - 1);
            }}
            className={
              currentPage === 1 ? "pointer-events-none opacity-50" : ""
            }
          />
        </PaginationItem>
        {pages.map((p, idx) =>
          p === "…" ? (
            <PaginationItem key={`e${idx}`}>
              <PaginationEllipsis />
            </PaginationItem>
          ) : (
            <PaginationItem key={p}>
              <PaginationLink
                href="#"
                isActive={p === currentPage}
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
              if (currentPage < totalPages) onChange(currentPage + 1);
            }}
            className={
              currentPage === totalPages
                ? "pointer-events-none opacity-50"
                : ""
            }
          />
        </PaginationItem>
      </PaginationContent>
    </Pagination>
  );
}
