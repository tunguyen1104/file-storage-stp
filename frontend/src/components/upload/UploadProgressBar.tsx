"use client";

import { Progress } from "@/components/ui/progress";
import type { UploadStatus } from "@/types/upload/upload.types";
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
  if (status === "queued" || status === "done" || status === "cancelled") {
    return null;
  }

  const isIndeterminate = status === "scanning" || status === "confirming";

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
            status === "error" || status === "infected"
              ? "[&>div]:bg-destructive"
              : "[&>div]:bg-primary"
          )}
        />
      )}
    </div>
  );
}
