"use client";

import { ArrowDown, ArrowUp, ArrowUpDown, ChevronRight } from "lucide-react";

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import type {
  AuditLogEntry,
  AuditLogSortBy,
  AuditSortDirection,
} from "@/types/audit/audit";
import { AUDIT_ACTION_LABELS } from "@/types/audit/audit";
import {
  ACTOR_ROLE_LABELS,
  SEVERITY_STYLES,
  actorRoleChipClass,
} from "@/components/audit-logs/audit-styles";
import {
  formatAbsolute,
  formatRelativeTime,
} from "@/components/audit-logs/audit-format";

type Props = {
  entries: AuditLogEntry[];
  isLoading: boolean;
  showTenantColumn: boolean;
  tenantNameById: Record<string, { name: string; slug: string }>;
  sortBy: AuditLogSortBy;
  sortDir: AuditSortDirection;
  onSortChange: (sortBy: AuditLogSortBy, sortDir: AuditSortDirection) => void;
  onSelect: (entry: AuditLogEntry) => void;
};

export function AuditLogTable({
  entries,
  isLoading,
  showTenantColumn,
  tenantNameById,
  sortBy,
  sortDir,
  onSortChange,
  onSelect,
}: Props) {
  function toggleSort(column: AuditLogSortBy) {
    if (sortBy === column) {
      onSortChange(column, sortDir === "ASC" ? "DESC" : "ASC");
    } else {
      onSortChange(column, "DESC");
    }
  }

  const colCount = showTenantColumn ? 7 : 6;

  return (
    <TooltipProvider delayDuration={200}>
      <div className="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow className="border-b border-gray-100 bg-gray-50/60 hover:bg-gray-50/60">
              <TableHead className="w-[44px] pl-6" />
              <TableHead className="px-2 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                <SortHeader
                  label="Thời điểm"
                  active={sortBy === "CREATED_AT"}
                  dir={sortDir}
                  onClick={() => toggleSort("CREATED_AT")}
                />
              </TableHead>
              <TableHead className="px-2 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                <SortHeader
                  label="Hành động"
                  active={sortBy === "ACTION"}
                  dir={sortDir}
                  onClick={() => toggleSort("ACTION")}
                />
              </TableHead>
              <TableHead className="px-2 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                Người thực hiện
              </TableHead>
              {showTenantColumn && (
                <TableHead className="px-2 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                  Tổ chức
                </TableHead>
              )}
              <TableHead className="px-2 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                Địa chỉ IP
              </TableHead>
              <TableHead className="w-[60px] pr-6 text-right" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading
              ? Array.from({ length: 6 }).map((_, i) => (
                  <SkeletonRow key={i} colCount={colCount} />
                ))
              : entries.length === 0
                ? (
                    <TableRow>
                      <TableCell
                        colSpan={colCount}
                        className="h-32 text-center text-sm text-gray-500"
                      >
                        Không có sự kiện nào khớp với bộ lọc hiện tại.
                      </TableCell>
                    </TableRow>
                  )
                : entries.map((entry) => (
                    <Row
                      key={entry.id}
                      entry={entry}
                      showTenantColumn={showTenantColumn}
                      tenantNameById={tenantNameById}
                      onSelect={onSelect}
                    />
                  ))}
          </TableBody>
        </Table>
      </div>
    </TooltipProvider>
  );
}

function Row({
  entry,
  showTenantColumn,
  tenantNameById,
  onSelect,
}: {
  entry: AuditLogEntry;
  showTenantColumn: boolean;
  tenantNameById: Record<string, { name: string; slug: string }>;
  onSelect: (entry: AuditLogEntry) => void;
}) {
  const sev = SEVERITY_STYLES[entry.severity];
  const tenant = entry.tenantId ? tenantNameById[entry.tenantId] : undefined;

  return (
    <TableRow
      onClick={() => onSelect(entry)}
      className="group cursor-pointer border-b border-gray-50 transition-colors hover:bg-violet-50/40"
    >
      <TableCell className="pl-6">
        <Tooltip>
          <TooltipTrigger asChild>
            <span
              className={cn(
                "inline-block h-2.5 w-2.5 rounded-full",
                sev.dot
              )}
              aria-label={sev.label}
            />
          </TooltipTrigger>
          <TooltipContent>{sev.label}</TooltipContent>
        </Tooltip>
      </TableCell>

      <TableCell className="px-2 text-sm text-gray-700">
        <Tooltip>
          <TooltipTrigger asChild>
            <span className="cursor-help font-medium text-gray-900">
              {formatRelativeTime(entry.createdAt)}
            </span>
          </TooltipTrigger>
          <TooltipContent>
            <span className="font-mono text-xs">
              {formatAbsolute(entry.createdAt)}
            </span>
          </TooltipContent>
        </Tooltip>
      </TableCell>

      <TableCell className="px-2">
        <span className="text-sm font-medium text-gray-900">
          {AUDIT_ACTION_LABELS[entry.action]}
        </span>
      </TableCell>

      <TableCell className="px-2 align-top">
        <div className="flex min-w-0 flex-col">
          <span className="truncate text-sm font-medium text-gray-900">
            {entry.userEmail ?? (
              <span className="italic text-gray-500">
                {entry.actorRole === "SYSTEM"
                  ? "Hệ thống"
                  : entry.actorRole === "ANONYMOUS"
                    ? "Ẩn danh"
                    : entry.userId
                      ? entry.userId.slice(0, 8) + "…"
                      : "—"}
              </span>
            )}
          </span>
          {entry.actorRole && (
            <span
              className={cn(
                "mt-0.5 inline-flex w-fit items-center rounded-full border px-1.5 py-px text-[10px] font-medium",
                actorRoleChipClass(entry.actorRole)
              )}
            >
              {ACTOR_ROLE_LABELS[entry.actorRole]}
            </span>
          )}
        </div>
      </TableCell>

      {showTenantColumn && (
        <TableCell className="px-2 text-sm">
          {tenant ? (
            <div className="flex min-w-0 flex-col">
              <span className="truncate text-gray-900">{tenant.name}</span>
              <span className="truncate text-[11px] text-gray-500">
                {tenant.slug}
              </span>
            </div>
          ) : entry.tenantId ? (
            <span className="font-mono text-xs text-gray-500">
              {entry.tenantId.slice(0, 8)}…
            </span>
          ) : (
            <span className="italic text-gray-400">—</span>
          )}
        </TableCell>
      )}

      <TableCell className="px-2 font-mono text-xs text-gray-600">
        {entry.ipAddress ?? <span className="italic text-gray-400">—</span>}
      </TableCell>

      <TableCell className="pr-6 text-right">
        <Button
          variant="ghost"
          size="icon"
          className="h-7 w-7 opacity-0 transition-opacity group-hover:opacity-100"
          onClick={(e) => {
            e.stopPropagation();
            onSelect(entry);
          }}
          aria-label="Xem chi tiết"
        >
          <ChevronRight className="h-4 w-4 text-gray-500" />
        </Button>
      </TableCell>
    </TableRow>
  );
}

function SortHeader({
  label,
  active,
  dir,
  onClick,
}: {
  label: string;
  active: boolean;
  dir: AuditSortDirection;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "inline-flex items-center gap-1 transition-colors hover:text-gray-700",
        active && "text-gray-900"
      )}
    >
      {label}
      {!active && <ArrowUpDown className="h-3 w-3" />}
      {active && dir === "ASC" && <ArrowUp className="h-3 w-3" />}
      {active && dir === "DESC" && <ArrowDown className="h-3 w-3" />}
    </button>
  );
}

function SkeletonRow({ colCount }: { colCount: number }) {
  return (
    <TableRow className="border-b border-gray-50">
      {Array.from({ length: colCount }).map((_, i) => (
        <TableCell key={i} className={cn(i === 0 && "pl-6")}>
          <Skeleton className="h-4 w-full max-w-[200px]" />
        </TableCell>
      ))}
    </TableRow>
  );
}

