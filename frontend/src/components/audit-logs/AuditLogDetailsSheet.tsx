"use client";

import { ArrowUpRight, Copy } from "lucide-react";
import { useState } from "react";

import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { cn } from "@/lib/utils";
import { toast } from "sonner";
import type { AuditLogEntry } from "@/types/audit/audit";
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
import {
  openFileHit,
} from "@/components/search/search-helpers";

const FILE_ACTIONS = new Set([
  "FILE_UPLOAD",
  "FILE_DOWNLOAD",
  "FILE_DELETE",
  "FILE_RESTORE",
  "FILE_PURGED",
]);

type Props = {
  entry: AuditLogEntry | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

export function AuditLogDetailsSheet({ entry, open, onOpenChange }: Props) {
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        className="w-full sm:max-w-xl flex flex-col"
      >
        <SheetHeader className="space-y-1 border-b border-gray-100 p-6">
          <SheetTitle className="text-lg">Chi tiết sự kiện</SheetTitle>
          <SheetDescription>
            Bản ghi nhật ký được lưu trữ bất biến.
          </SheetDescription>
        </SheetHeader>

        {entry && (
          <ScrollArea className="flex-1">
            <div className="space-y-6 p-6">
              <SummaryBlock entry={entry} />
              <DetailsGrid entry={entry} />
              <ExtraDataBlock data={entry.extraData} />
            </div>
          </ScrollArea>
        )}
      </SheetContent>
    </Sheet>
  );
}

function SummaryBlock({ entry }: { entry: AuditLogEntry }) {
  const sev = SEVERITY_STYLES[entry.severity];
  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2">
        <Badge
          variant="outline"
          className={cn("gap-1.5 border", sev.chip)}
        >
          <span className={cn("h-1.5 w-1.5 rounded-full", sev.dot)} />
          {sev.label}
        </Badge>
        <Badge variant="secondary">{AUDIT_ACTION_LABELS[entry.action]}</Badge>
      </div>
      <div className="rounded-lg border border-gray-200 bg-gray-50 px-4 py-3 text-sm">
        <div className="font-medium text-gray-900">
          {entry.userEmail ?? (
            <span className="italic text-gray-500">Hệ thống / Ẩn danh</span>
          )}
        </div>
        {entry.actorRole && (
          <span
            className={cn(
              "mt-1 inline-flex items-center rounded-full border px-2 py-0.5 text-[11px] font-medium",
              actorRoleChipClass(entry.actorRole)
            )}
          >
            {ACTOR_ROLE_LABELS[entry.actorRole]}
          </span>
        )}
        <div className="mt-2 text-xs text-gray-500">
          {formatRelativeTime(entry.createdAt)} ·{" "}
          <span className="font-mono">{formatAbsolute(entry.createdAt)}</span>
        </div>
      </div>
    </div>
  );
}

function DetailsGrid({ entry }: { entry: AuditLogEntry }) {
  const fileId = entry.fileId;
  const showFileLink = Boolean(fileId) && FILE_ACTIONS.has(entry.action);
  return (
    <dl className="space-y-3">
      <Row label="ID sự kiện" value={entry.id} copyable mono />
      <Row label="Tổ chức (resource)" value={entry.tenantId ?? null} mono />
      <Row label="User ID" value={entry.userId ?? null} mono />
      {fileId && (
        <Row label="File ID" value={fileId} mono>
          {showFileLink && (
            <button
              type="button"
              onClick={() => void openFileHit(fileId)}
              className="ml-2 inline-flex items-center gap-0.5 text-xs font-medium text-violet-700 hover:underline"
            >
              Mở tệp
              <ArrowUpRight className="h-3 w-3" />
            </button>
          )}
        </Row>
      )}
      <Row label="Địa chỉ IP" value={entry.ipAddress ?? null} mono />
      <Row label="Request ID" value={entry.requestId ?? null} copyable mono />
      <Row
        label="User-Agent"
        value={entry.userAgent ?? null}
        breakAll
      />
    </dl>
  );
}

function Row({
  label,
  value,
  mono,
  copyable,
  breakAll,
  children,
}: {
  label: string;
  value: string | null;
  mono?: boolean;
  copyable?: boolean;
  breakAll?: boolean;
  children?: React.ReactNode;
}) {
  return (
    <div>
      <dt className="text-[11px] font-semibold uppercase tracking-wider text-gray-500">
        {label}
      </dt>
      <dd
        className={cn(
          "mt-1 flex items-start gap-2 text-sm text-gray-900",
          mono && "font-mono text-xs",
          breakAll && "break-all"
        )}
      >
        {value ? (
          <span className="flex-1">{value}</span>
        ) : (
          <span className="flex-1 italic text-gray-400">—</span>
        )}
        {copyable && value && <CopyButton text={value} />}
        {children}
      </dd>
    </div>
  );
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      toast.success("Đã sao chép vào bộ nhớ tạm");
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      toast.error("Không thể sao chép");
    }
  }
  return (
    <Button
      type="button"
      size="icon"
      variant="ghost"
      className="h-6 w-6 shrink-0 text-gray-400 hover:text-gray-700"
      onClick={handleCopy}
      aria-label="Sao chép"
    >
      <Copy className={cn("h-3.5 w-3.5", copied && "text-emerald-600")} />
    </Button>
  );
}

function ExtraDataBlock({ data }: { data: Record<string, unknown> | null | undefined }) {
  if (!data || Object.keys(data).length === 0) return null;
  const json = JSON.stringify(data, null, 2);
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <h3 className="text-[11px] font-semibold uppercase tracking-wider text-gray-500">
          Dữ liệu bổ sung
        </h3>
        <CopyButton text={json} />
      </div>
      <pre className="overflow-x-auto rounded-lg bg-gray-900 px-4 py-3 text-[11px] leading-relaxed text-emerald-200">
        {json}
      </pre>
    </div>
  );
}
