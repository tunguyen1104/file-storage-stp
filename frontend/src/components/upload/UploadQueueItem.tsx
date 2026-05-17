"use client";

import {
  FileImage,
  FileText,
  FileArchive,
  FileVideo,
  FileAudio,
  File as FileIcon,
  X,
  RotateCcw,
  Play,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { UploadStatusBadge } from "@/components/upload/UploadStatusBadge";
import { UploadProgressBar } from "@/components/upload/UploadProgressBar";
import { formatBytes, formatSpeed, formatEta } from "@/lib/utils/upload-utils";
import type { UploadItem } from "@/types/upload/upload.types";
import { UPLOAD_STATUS } from "@/lib/constants";
import { cn } from "@/lib/utils";

const ACTIVE_QUEUE_STATUSES = [
  UPLOAD_STATUS.QUEUED,
  UPLOAD_STATUS.UPLOADING,
  UPLOAD_STATUS.CONFIRMING,
  UPLOAD_STATUS.SCANNING,
] as const;

const IN_FLIGHT_PROGRESS_STATUSES = [
  UPLOAD_STATUS.UPLOADING,
  UPLOAD_STATUS.CONFIRMING,
  UPLOAD_STATUS.SCANNING,
] as const;

// ─── File icon helper ─────────────────────────────────────────────────────────

function FileTypeIcon({ mimeType, className }: { mimeType: string; className?: string }) {
  const cls = cn("shrink-0", className);
  if (mimeType.startsWith("image/")) return <FileImage className={cn(cls, "text-sky-500")} />;
  if (mimeType.startsWith("video/")) return <FileVideo className={cn(cls, "text-purple-500")} />;
  if (mimeType.startsWith("audio/")) return <FileAudio className={cn(cls, "text-orange-500")} />;
  if (
    mimeType.includes("zip") ||
    mimeType.includes("tar") ||
    mimeType.includes("rar") ||
    mimeType.includes("7z")
  )
    return <FileArchive className={cn(cls, "text-amber-500")} />;
  if (
    mimeType.includes("pdf") ||
    mimeType.includes("document") ||
    mimeType.startsWith("text/")
  )
    return <FileText className={cn(cls, "text-blue-500")} />;
  return <FileIcon className={cn(cls, "text-muted-foreground")} />;
}

// ─── Props ────────────────────────────────────────────────────────────────────

interface UploadQueueItemProps {
  item: UploadItem;
  compact?: boolean;
  onCancel: () => void;
  onRetry: () => void;
  onResume: () => void;
}

// ─── Component ────────────────────────────────────────────────────────────────

export function UploadQueueItem({
  item,
  compact = false,
  onCancel,
  onRetry,
  onResume,
}: UploadQueueItemProps) {
  const isActive = (ACTIVE_QUEUE_STATUSES as readonly string[]).includes(item.status);
  const isError = item.status === UPLOAD_STATUS.ERROR;
  const canResume = isError && !!item.uploadId;
  const isDone = item.status === UPLOAD_STATUS.DONE;
  const mimeType = item.file.type || "application/octet-stream";

  return (
    <div
      className={cn(
        "flex flex-col gap-1 rounded-lg border bg-card p-3 transition-colors",
        compact ? "text-xs" : "text-sm",
        isDone && "opacity-70"
      )}
    >
      {/* Top row: icon + name + size + badge + actions */}
      <div className="flex items-start gap-2">
        <FileTypeIcon
          mimeType={mimeType}
          className={compact ? "h-4 w-4 mt-0.5" : "h-5 w-5 mt-0.5"}
        />

        <div className="flex-1 min-w-0">
          <p
            className={cn(
              "font-medium truncate leading-tight",
              compact ? "max-w-[180px]" : "max-w-[260px]"
            )}
            title={item.file.name}
          >
            {item.file.name}
          </p>
          <p className="text-muted-foreground text-xs">{formatBytes(item.file.size)}</p>
        </div>

        <div className="flex items-center gap-1 shrink-0">
          <UploadStatusBadge status={item.status} progress={item.progress} />

          {/* Action buttons */}
          {isActive && (
            <Button
              variant="ghost"
              size="icon"
              className="h-6 w-6 text-muted-foreground hover:text-destructive"
              onClick={onCancel}
              title="Cancel"
            >
              <X className="h-3.5 w-3.5" />
            </Button>
          )}
          {isError && canResume && (
            <Button
              variant="ghost"
              size="icon"
              className="h-6 w-6 text-primary"
              onClick={onResume}
              title="Resume"
            >
              <Play className="h-3.5 w-3.5" />
            </Button>
          )}
          {isError && !canResume && item.status !== UPLOAD_STATUS.INFECTED && (
            <Button
              variant="ghost"
              size="icon"
              className="h-6 w-6 text-primary"
              onClick={onRetry}
              title="Retry"
            >
              <RotateCcw className="h-3.5 w-3.5" />
            </Button>
          )}
        </div>
      </div>

      {/* Progress bar */}
      {(IN_FLIGHT_PROGRESS_STATUSES as readonly string[]).includes(item.status) && (
        <UploadProgressBar
          status={item.status}
          progress={item.progress}
          className="mt-0.5"
        />
      )}

      {/* Speed + ETA row */}
      {item.status === UPLOAD_STATUS.UPLOADING && (item.speedBps || item.etaSeconds) && (
        <div className="flex items-center gap-2 text-xs text-muted-foreground mt-0.5">
          {item.speedBps !== undefined && (
            <span>{formatSpeed(item.speedBps)}</span>
          )}
          {item.etaSeconds !== undefined && (
            <span>· {formatEta(item.etaSeconds)} remaining</span>
          )}
        </div>
      )}

      {/* Error message */}
      {isError && item.errorMessage && (
        <p className="text-xs text-destructive mt-0.5 line-clamp-2 break-words">{item.errorMessage}</p>
      )}
    </div>
  );
}
