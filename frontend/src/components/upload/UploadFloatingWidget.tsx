"use client";

import { useState } from "react";
import { usePathname } from "next/navigation";
import { UploadCloud, ChevronUp } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { useUpload } from "@/contexts/upload-context";
import { UploadQueueItem } from "@/components/upload/UploadQueueItem";
import { UPLOAD_STATUS, UPLOAD_FAILURE_STATUSES } from "@/lib/constants";
import { cn } from "@/lib/utils";

const ACTIVE_WIDGET_STATUSES = [
  UPLOAD_STATUS.QUEUED,
  UPLOAD_STATUS.UPLOADING,
  UPLOAD_STATUS.CONFIRMING,
  UPLOAD_STATUS.SCANNING,
] as const;

export function UploadFloatingWidget() {
  const { uploadQueue, cancelUpload, retryUpload, resumeUpload, clearDone, clearErrors } = useUpload();
  const [expanded, setExpanded] = useState(true);
  const pathname = usePathname();

  // Hide when queue is empty or on the /upload page (full panel is shown there)
  if (uploadQueue.length === 0 || pathname === "/upload") return null;

  const activeCount = uploadQueue.filter((i) =>
    (ACTIVE_WIDGET_STATUSES as readonly string[]).includes(i.status)
  ).length;
  const doneCount = uploadQueue.filter((i) => i.status === UPLOAD_STATUS.DONE).length;
  const errorCount = uploadQueue.filter((i) =>
    UPLOAD_FAILURE_STATUSES.includes(i.status)
  ).length;

  return (
    <div className="fixed bottom-4 right-4 z-50 w-96 rounded-xl border bg-background shadow-2xl overflow-hidden">
      {/* Header */}
      <div
        className="flex cursor-pointer select-none items-center justify-between bg-muted/50 px-4 py-3"
        onClick={() => setExpanded((e) => !e)}
      >
        <div className="flex items-center gap-2">
          <UploadCloud className="h-4 w-4 text-primary shrink-0" />
          <span className="text-sm font-medium">
            {activeCount > 0 ? `Đang tải lên ${activeCount} tệp…` : "Danh sách tải lên"}
          </span>
        </div>
        <div className="flex items-center gap-1.5">
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

      {/* Body */}
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
