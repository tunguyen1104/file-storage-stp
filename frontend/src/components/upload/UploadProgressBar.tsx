"use client";

import { Progress } from "@/components/ui/progress";
import type { UploadStatus } from "@/types/upload/upload.types";
import { UPLOAD_STATUS } from "@/lib/constants";
import { cn } from "@/lib/utils";

interface UploadProgressBarProps {
  status: UploadStatus;
  progress: number;
  className?: string;
}

export function UploadProgressBar({
  status,
  progress,
  className,
}: UploadProgressBarProps) {
  if (
    status === UPLOAD_STATUS.QUEUED ||
    status === UPLOAD_STATUS.DONE ||
    status === UPLOAD_STATUS.CANCELLED
  ) {
    return null;
  }

  const isIndeterminate =
    status === UPLOAD_STATUS.SCANNING || status === UPLOAD_STATUS.CONFIRMING;

  return (
    <div className={cn("w-full", className)}>
      {isIndeterminate ? (
        <div className="h-1.5 w-full overflow-hidden rounded-full bg-secondary">
          <div className="h-full w-1/3 animate-[slide_1.5s_ease-in-out_infinite] rounded-full bg-amber-400" />
        </div>
      ) : (
        <Progress
          value={progress}
          className={cn(
            "h-1.5",
            status === UPLOAD_STATUS.ERROR || status === UPLOAD_STATUS.INFECTED
              ? "[&>div]:bg-destructive"
              : "[&>div]:bg-primary"
          )}
        />
      )}
    </div>
  );
}
