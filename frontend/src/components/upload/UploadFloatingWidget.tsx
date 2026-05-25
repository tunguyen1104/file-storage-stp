"use client";

import { useRef, useState } from "react";
import { AlertTriangle, UploadCloud, ChevronUp, X } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { useUpload } from "@/contexts/upload-context";
import { UploadQueueItem } from "@/components/upload/UploadQueueItem";
import { UPLOAD_STATUS, UPLOAD_FAILURE_STATUSES } from "@/lib/constants";
import { formatBytes } from "@/lib/utils/upload-utils";
import { cn } from "@/lib/utils";
import type { PendingMultipartRecord } from "@/types/upload/upload.types";

const ACTIVE_WIDGET_STATUSES = [
  UPLOAD_STATUS.QUEUED,
  UPLOAD_STATUS.UPLOADING,
  UPLOAD_STATUS.CONFIRMING,
  UPLOAD_STATUS.SCANNING,
] as const;

// ─── Interrupted upload row ───────────────────────────────────────────────────

function InterruptedUploadRow({
  record,
  onResume,
  onDiscard,
}: {
  record: PendingMultipartRecord;
  onResume: (record: PendingMultipartRecord, file: File) => void;
  onDiscard: (uploadId: string) => void;
}) {
  const fileInputRef = useRef<HTMLInputElement>(null);

  function handleFileSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    if (file.name !== record.fileName || file.size !== record.fileSizeBytes) {
      toast.error("Tệp tin không khớp. Vui lòng chọn tệp tin gốc để tiếp tục.");
      e.target.value = "";
      return;
    }
    onResume(record, file);
  }

  return (
    <div className="flex flex-col gap-2 rounded-lg border border-amber-500/40 bg-amber-50/50 dark:bg-amber-950/20 p-3">
      <div className="flex items-start gap-2">
        <AlertTriangle className="h-4 w-4 text-amber-500 shrink-0 mt-0.5" />
        <div className="flex-1 min-w-0">
          <p className="text-xs font-medium truncate leading-tight" title={record.fileName}>
            {record.fileName}
          </p>
          <p className="text-xs text-muted-foreground">{formatBytes(record.fileSizeBytes)}</p>
        </div>
        <Button
          variant="ghost"
          size="icon"
          className="h-6 w-6 text-muted-foreground hover:text-destructive shrink-0"
          title="Bỏ qua"
          onClick={() => onDiscard(record.uploadId)}
        >
          <X className="h-3.5 w-3.5" />
        </Button>
      </div>
      <Button
        size="sm"
        className="h-7 text-xs w-full"
        onClick={() => fileInputRef.current?.click()}
      >
        Chọn tệp để tiếp tục
      </Button>
      <input ref={fileInputRef} type="file" hidden onChange={handleFileSelect} />
    </div>
  );
}

// ─── Main widget ──────────────────────────────────────────────────────────────

export function UploadFloatingWidget() {
  const {
    uploadQueue,
    cancelUpload,
    retryUpload,
    resumeUpload,
    clearDone,
    clearErrors,
    interruptedUploads,
    resumeInterruptedUpload,
    discardInterruptedUpload,
  } = useUpload();
  const [expanded, setExpanded] = useState(true);

  if (uploadQueue.length === 0 && interruptedUploads.length === 0) return null;

  const activeCount = uploadQueue.filter((i) =>
    (ACTIVE_WIDGET_STATUSES as readonly string[]).includes(i.status)
  ).length;
  const doneCount = uploadQueue.filter((i) => i.status === UPLOAD_STATUS.DONE).length;
  const errorCount = uploadQueue.filter((i) =>
    UPLOAD_FAILURE_STATUSES.includes(i.status)
  ).length;

  const headerLabel =
    activeCount > 0
      ? `Đang tải lên ${activeCount} tệp…`
      : interruptedUploads.length > 0 && uploadQueue.length === 0
        ? "Tải lên bị gián đoạn"
        : "Danh sách tải lên";

  return (
    <div className="fixed bottom-4 right-4 z-50 w-96 rounded-xl border bg-background shadow-2xl overflow-hidden">
      {/* Header */}
      <div
        className="flex cursor-pointer select-none items-center justify-between bg-muted/50 px-4 py-3"
        onClick={() => setExpanded((e) => !e)}
      >
        <div className="flex items-center gap-2">
          <UploadCloud className="h-4 w-4 text-primary shrink-0" />
          <span className="text-sm font-medium">{headerLabel}</span>
        </div>
        <div className="flex items-center gap-1.5">
          {interruptedUploads.length > 0 && (
            <Badge className="text-xs bg-amber-500 hover:bg-amber-500 text-white border-0">
              {interruptedUploads.length} gián đoạn
            </Badge>
          )}
          {doneCount > 0 && (
            <Badge className="text-xs bg-green-500 hover:bg-green-500 text-white border-0">
              {doneCount} hoàn thành
            </Badge>
          )}
          {errorCount > 0 && (
            <Badge variant="destructive" className="text-xs">
              {errorCount} lỗi
            </Badge>
          )}
          <ChevronUp
            className={cn(
              "h-4 w-4 text-muted-foreground transition-transform duration-200",
              !expanded && "rotate-180"
            )}
          />
        </div>
      </div>

      {/* Body — queue items + interrupted upload rows */}
      {expanded && (
        <ScrollArea className="max-h-72">
          <div className="flex flex-col gap-1 p-2">
            {uploadQueue.map((item) => (
              <UploadQueueItem
                key={item.id}
                item={item}
                compact
                onCancel={() => cancelUpload(item.id)}
                onRetry={() => retryUpload(item.id)}
                onResume={() => resumeUpload(item.id)}
              />
            ))}
            {interruptedUploads.map((record) => (
              <InterruptedUploadRow
                key={record.uploadId}
                record={record}
                onResume={resumeInterruptedUpload}
                onDiscard={discardInterruptedUpload}
              />
            ))}
          </div>
        </ScrollArea>
      )}

      {/* Footer — clear completed / failed */}
      {expanded && (doneCount > 0 || errorCount > 0) && (
        <div className="border-t px-4 py-2 flex gap-2">
          {doneCount > 0 && (
            <Button
              variant="ghost"
              size="sm"
              className="flex-1 text-xs"
              onClick={clearDone}
            >
              Xóa đã hoàn thành
            </Button>
          )}
          {errorCount > 0 && (
            <Button
              variant="ghost"
              size="sm"
              className="flex-1 text-xs text-destructive hover:text-destructive hover:bg-destructive/10"
              onClick={clearErrors}
            >
              Xóa tệp lỗi
            </Button>
          )}
        </div>
      )}
    </div>
  );
}
