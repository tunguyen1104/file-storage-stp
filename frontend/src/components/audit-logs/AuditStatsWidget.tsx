"use client";

import { useEffect, useState } from "react";
import {
  AlertCircle,
  AlertTriangle,
  CheckCircle2,
  Database,
  Info,
  RefreshCw,
} from "lucide-react";
import type { ComponentType } from "react";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { useAuditLogStats } from "@/hooks/use-audit-logs";
import type { AuditSeverity } from "@/types/audit/audit";
import { SEVERITY_STYLES } from "@/components/audit-logs/audit-styles";
import { formatRelativeTime } from "@/components/audit-logs/audit-format";

type SeverityCard = {
  severity: AuditSeverity;
  icon: ComponentType<{ className?: string }>;
  label: string;
};

const CARDS: SeverityCard[] = [
  { severity: "ERROR", icon: AlertCircle, label: "Lỗi" },
  { severity: "WARNING", icon: AlertTriangle, label: "Cảnh báo" },
  { severity: "INFO", icon: Info, label: "Thông tin" },
  { severity: "SUCCESS", icon: CheckCircle2, label: "Thành công" },
];

type Props = {
  scopeLabel: string;
};

export function AuditStatsWidget({ scopeLabel }: Props) {
  const { data, isLoading, isValidating, mutate, error } = useAuditLogStats();
  const [updatedAt, setUpdatedAt] = useState<string | null>(null);
  const [, setTick] = useState(0);

  useEffect(() => {
    if (data && !isValidating) {
      setUpdatedAt(new Date().toISOString());
    }
  }, [data, isValidating]);

  // Re-render every 30s so the "updated X ago" label stays fresh.
  useEffect(() => {
    const id = window.setInterval(() => setTick((t) => t + 1), 30_000);
    return () => window.clearInterval(id);
  }, []);

  return (
    <section className="rounded-xl border border-gray-200 bg-white shadow-sm">
      <header className="flex flex-col gap-1 px-6 py-4 border-b border-gray-100 md:flex-row md:items-center md:justify-between">
        <div>
          <h2 className="text-base font-semibold text-gray-900">
            Tổng quan sự kiện
          </h2>
          <p className="text-xs text-gray-500">Toàn thời gian — {scopeLabel}</p>
        </div>
        <div className="flex items-center gap-3 text-xs text-gray-500">
          {updatedAt && (
            <span>Cập nhật {formatRelativeTime(updatedAt)}</span>
          )}
        </div>
      </header>

      <div className="grid grid-cols-2 gap-4 px-6 py-5 lg:grid-cols-5">
        <div className="rounded-lg border border-gray-200 bg-gray-50 p-4 col-span-2 lg:col-span-1">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-gray-200 text-gray-700">
              <Database className="h-4 w-4" />
            </div>
            <div>
              <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">
                Tất cả sự kiện
              </p>
              {isLoading ? (
                <Skeleton className="mt-1 h-7 w-20" />
              ) : (
                <p className="text-2xl font-bold text-gray-900 tabular-nums">
                  {(data?.total ?? 0).toLocaleString("vi-VN")}
                </p>
              )}
            </div>
          </div>
        </div>

        {CARDS.map(({ severity, icon: Icon, label }) => {
          const style = SEVERITY_STYLES[severity];
          const count = data?.severityCounts?.[severity] ?? 0;
          return (
            <div
              key={severity}
              className={cn(
                "rounded-lg border p-4 transition-colors",
                style.card
              )}
            >
              <div className="flex items-center gap-3">
                <div
                  className={cn(
                    "flex h-9 w-9 items-center justify-center rounded-lg",
                    style.cardIcon
                  )}
                >
                  <Icon className="h-4 w-4" />
                </div>
                <div>
                  <p
                    className={cn(
                      "text-xs font-medium uppercase tracking-wide",
                      style.cardText
                    )}
                  >
                    {label}
                  </p>
                  {isLoading ? (
                    <Skeleton className="mt-1 h-7 w-20" />
                  ) : (
                    <p className="text-2xl font-bold text-gray-900 tabular-nums">
                      {count.toLocaleString("vi-VN")}
                    </p>
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {error && (
        <p className="px-6 pb-4 text-sm text-red-600">
          Không tải được thống kê. Vui lòng thử lại.
        </p>
      )}
    </section>
  );
}
