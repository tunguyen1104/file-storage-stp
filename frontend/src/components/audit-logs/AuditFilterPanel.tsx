"use client";

import { useMemo, useState } from "react";
import {
  Calendar as CalendarIcon,
  Check,
  ChevronsUpDown,
  ListFilter,
  RotateCcw,
  Sliders,
  X,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import {
  AUDIT_ACTION_GROUPS,
  AUDIT_ACTION_LABELS,
  AUDIT_SEVERITIES,
  AUDIT_SEVERITY_LABELS,
} from "@/types/audit/audit";
import type {
  AuditAction,
  AuditSeverity,
} from "@/types/audit/audit";
import { SEVERITY_STYLES } from "@/components/audit-logs/audit-styles";
import {
  datetimeLocalToIso,
  toDatetimeLocal,
} from "@/components/audit-logs/audit-format";
import { TenantFilterDropdown } from "@/components/audit-logs/TenantFilterDropdown";
import { UserEmailAutocomplete } from "@/components/audit-logs/UserEmailAutocomplete";

export type AuditFilters = {
  userEmail: string;
  actions: AuditAction[];
  fileId: string;
  tenantId?: string;
  from?: string;
  to?: string;
  ipAddress: string;
  userAgent: string;
  /** Client-side only. Pages reset when this changes via list dependency. */
  severities: AuditSeverity[];
  /**
   * If set, the time window is a "rolling N days" — `to` should slide forward
   * to "now" each time the user explicitly refreshes. Cleared when the user
   * manually edits the datetime inputs (custom / frozen range).
   */
  quickRangeDays?: number;
};

export const DEFAULT_RANGE_DAYS = 30;

export function computeDefaultRange(days: number) {
  const to = new Date();
  const from = new Date(to.getTime() - days * 24 * 60 * 60 * 1000);
  return {
    fromIso: from.toISOString(),
    toIso: to.toISOString(),
  };
}

export const EMPTY_FILTERS: AuditFilters = {
  userEmail: "",
  actions: [],
  fileId: "",
  tenantId: undefined,
  from: undefined,
  to: undefined,
  ipAddress: "",
  userAgent: "",
  severities: [],
  quickRangeDays: undefined,
};

type QuickRange = {
  label: string;
  days: number;
};

const QUICK_RANGES: QuickRange[] = [
  { label: "24 giờ", days: 1 },
  { label: "7 ngày", days: 7 },
  { label: "30 ngày", days: 30 },
  { label: "90 ngày", days: 90 },
];

type Props = {
  filters: AuditFilters;
  onChange: (next: AuditFilters) => void;
  onReset: () => void;
  showTenantFilter: boolean;
  activeFilterCount: number;
};

export function AuditFilterPanel({
  filters,
  onChange,
  onReset,
  showTenantFilter,
  activeFilterCount,
}: Props) {
  const [showAdvanced, setShowAdvanced] = useState(
    Boolean(filters.ipAddress || filters.userAgent || filters.fileId)
  );

  function setFilter<K extends keyof AuditFilters>(
    key: K,
    value: AuditFilters[K]
  ) {
    onChange({ ...filters, [key]: value });
  }

  function applyQuickRange(days: number) {
    const { fromIso, toIso } = computeDefaultRange(days);
    onChange({
      ...filters,
      from: fromIso,
      to: toIso,
      quickRangeDays: days,
    });
  }

  function setCustomDate(key: "from" | "to", value: string | undefined) {
    // User typed in datetime input → window is frozen, no longer rolling.
    onChange({ ...filters, [key]: value, quickRangeDays: undefined });
  }

  function toggleAction(action: AuditAction) {
    const present = filters.actions.includes(action);
    setFilter(
      "actions",
      present
        ? filters.actions.filter((a) => a !== action)
        : [...filters.actions, action]
    );
  }

  function toggleSeverity(sev: AuditSeverity) {
    const present = filters.severities.includes(sev);
    setFilter(
      "severities",
      present
        ? filters.severities.filter((s) => s !== sev)
        : [...filters.severities, sev]
    );
  }

  const fromValue = useMemo(
    () => (filters.from ? toDatetimeLocal(new Date(filters.from)) : ""),
    [filters.from]
  );
  const toValue = useMemo(
    () => (filters.to ? toDatetimeLocal(new Date(filters.to)) : ""),
    [filters.to]
  );

  return (
    <section className="rounded-xl border border-gray-200 bg-white shadow-sm">
      <header className="flex flex-col gap-2 px-6 py-4 border-b border-gray-100 md:flex-row md:items-center md:justify-between">
        <div className="flex items-center gap-2">
          <ListFilter className="h-4 w-4 text-gray-500" />
          <h2 className="text-sm font-semibold text-gray-900">Bộ lọc</h2>
          {activeFilterCount > 0 && (
            <Badge variant="secondary" className="ml-1">
              {activeFilterCount} bộ lọc đang áp dụng
            </Badge>
          )}
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setShowAdvanced((s) => !s)}
            className="h-8 gap-1.5 text-xs text-gray-600"
          >
            <Sliders className="h-3.5 w-3.5" />
            {showAdvanced ? "Ẩn nâng cao" : "Nâng cao"}
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={onReset}
            disabled={activeFilterCount === 0}
            className="h-8 gap-1.5 text-xs text-red-600 hover:bg-red-50 hover:text-red-700"
          >
            <RotateCcw className="h-3.5 w-3.5" />
            Xoá tất cả
          </Button>
        </div>
      </header>

      <div className="grid gap-4 px-6 py-5 md:grid-cols-2 lg:grid-cols-3">
        {/* Date range */}
        <div className="space-y-2 lg:col-span-2">
          <Label className="text-xs font-medium text-gray-600">
            Khoảng thời gian
          </Label>
          <div className="flex flex-wrap gap-2">
            {QUICK_RANGES.map((q) => {
              const active = filters.quickRangeDays === q.days;
              return (
                <Button
                  key={q.label}
                  variant={active ? "default" : "outline"}
                  size="sm"
                  className="h-8 text-xs"
                  onClick={() => applyQuickRange(q.days)}
                >
                  <CalendarIcon className="mr-1.5 h-3 w-3" />
                  {q.label}
                </Button>
              );
            })}
          </div>
          <div className="flex flex-wrap gap-3">
            <div className="space-y-1">
              <Label className="text-[11px] text-gray-500">Từ</Label>
              <Input
                type="datetime-local"
                value={fromValue}
                onChange={(e) =>
                  setCustomDate("from", datetimeLocalToIso(e.target.value))
                }
                className="h-9 w-[220px]"
              />
            </div>
            <div className="space-y-1">
              <Label className="text-[11px] text-gray-500">Đến</Label>
              <Input
                type="datetime-local"
                value={toValue}
                onChange={(e) =>
                  setCustomDate("to", datetimeLocalToIso(e.target.value))
                }
                className="h-9 w-[220px]"
              />
            </div>
          </div>
          {filters.from && filters.to && filters.from > filters.to && (
            <p className="text-xs text-red-600">
              Thời gian “từ” phải nhỏ hơn “đến”.
            </p>
          )}
        </div>

        {/* Action multi-select */}
        <div className="space-y-2">
          <Label className="text-xs font-medium text-gray-600">Hành động</Label>
          <ActionMultiSelect
            value={filters.actions}
            onToggle={toggleAction}
            onClear={() => setFilter("actions", [])}
          />
        </div>

        {/* Severity chips (client-side) */}
        <div className="space-y-2 lg:col-span-2">
          <Label className="text-xs font-medium text-gray-600">
            Mức độ (lọc tại trang hiện tại)
          </Label>
          <div className="flex flex-wrap gap-2">
            {AUDIT_SEVERITIES.map((sev) => {
              const style = SEVERITY_STYLES[sev];
              const active = filters.severities.includes(sev);
              return (
                <button
                  key={sev}
                  type="button"
                  onClick={() => toggleSeverity(sev)}
                  className={cn(
                    "inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-colors",
                    active ? style.chip : "border-gray-200 bg-white text-gray-600 hover:bg-gray-50"
                  )}
                >
                  <span className={cn("h-1.5 w-1.5 rounded-full", style.dot)} />
                  {AUDIT_SEVERITY_LABELS[sev]}
                </button>
              );
            })}
          </div>
        </div>

        {/* User email autocomplete */}
        <div className="space-y-2">
          <Label className="text-xs font-medium text-gray-600">
            Người thực hiện
          </Label>
          <UserEmailAutocomplete
            value={filters.userEmail}
            onChange={(email) => setFilter("userEmail", email)}
            tenantId={filters.tenantId}
          />
        </div>

        {/* Tenant filter (SUPER_ADMIN only) */}
        {showTenantFilter && (
          <div className="space-y-2">
            <Label className="text-xs font-medium text-gray-600">Tổ chức</Label>
            <TenantFilterDropdown
              value={filters.tenantId}
              onChange={(id) => setFilter("tenantId", id)}
            />
          </div>
        )}
      </div>

      {showAdvanced && (
        <div className="grid gap-4 border-t border-gray-100 bg-gray-50/60 px-6 py-5 md:grid-cols-3">
          <div className="space-y-2">
            <Label className="text-xs font-medium text-gray-600">
              Địa chỉ IP (khớp chính xác)
            </Label>
            <Input
              value={filters.ipAddress}
              onChange={(e) => setFilter("ipAddress", e.target.value)}
              placeholder="203.0.113.42"
              className="h-9"
            />
          </div>
          <div className="space-y-2">
            <Label className="text-xs font-medium text-gray-600">
              User-Agent (chứa)
            </Label>
            <Input
              value={filters.userAgent}
              onChange={(e) => setFilter("userAgent", e.target.value)}
              placeholder="Tìm chuỗi con trong User-Agent..."
              className="h-9"
              maxLength={256}
            />
          </div>
          <div className="space-y-2">
            <Label className="text-xs font-medium text-gray-600">File ID</Label>
            <Input
              value={filters.fileId}
              onChange={(e) => setFilter("fileId", e.target.value)}
              placeholder="UUID tệp tin"
              className="h-9 font-mono text-xs"
            />
          </div>
        </div>
      )}
    </section>
  );
}

function ActionMultiSelect({
  value,
  onToggle,
  onClear,
}: {
  value: AuditAction[];
  onToggle: (a: AuditAction) => void;
  onClear: () => void;
}) {
  const [open, setOpen] = useState(false);
  const summary =
    value.length === 0
      ? "Tất cả hành động"
      : value.length === 1
        ? AUDIT_ACTION_LABELS[value[0]]
        : `${value.length} hành động đã chọn`;

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          className="h-10 w-full justify-between gap-2 font-normal"
        >
          <span className="truncate text-left">{summary}</span>
          <div className="flex items-center gap-1.5">
            {value.length > 0 && (
              <span
                onClick={(e) => {
                  e.stopPropagation();
                  onClear();
                }}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    onClear();
                  }
                }}
                className="rounded p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600"
                aria-label="Xoá lựa chọn"
              >
                <X className="h-3 w-3" />
              </span>
            )}
            <ChevronsUpDown className="h-4 w-4 text-gray-400" />
          </div>
        </Button>
      </PopoverTrigger>
      <PopoverContent
        align="start"
        className="w-[--radix-popover-trigger-width] min-w-[280px] max-h-80 overflow-y-auto p-0"
      >
        {AUDIT_ACTION_GROUPS.map((group) => (
          <div key={group.label} className="border-b border-gray-100 last:border-b-0">
            <div className="bg-gray-50 px-3 py-1.5 text-[11px] font-semibold uppercase tracking-wider text-gray-500">
              {group.label}
            </div>
            <ul>
              {group.actions.map((a) => {
                const checked = value.includes(a);
                return (
                  <li key={a}>
                    <button
                      type="button"
                      onClick={() => onToggle(a)}
                      className={cn(
                        "flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm hover:bg-violet-50",
                        checked && "bg-violet-50"
                      )}
                    >
                      <span className="truncate">{AUDIT_ACTION_LABELS[a]}</span>
                      {checked && (
                        <Check className="h-4 w-4 text-violet-700" />
                      )}
                    </button>
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </PopoverContent>
    </Popover>
  );
}
