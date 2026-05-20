"use client";

import { Badge } from "@/components/ui/badge";
import type { UploadStatus } from "@/types/upload/upload.types";
import { UPLOAD_STATUS } from "@/lib/constants";
import { cn } from "@/lib/utils";

interface UploadStatusBadgeProps {
  status: UploadStatus;
  progress?: number;
  className?: string;
}

const STATUS_CONFIG: Record<
  UploadStatus,
  { label: (progress?: number) => string; className: string }
> = {
  [UPLOAD_STATUS.QUEUED]: {
    label: () => "Đang chờ",
    className:
      "border-gray-200 bg-gray-50 text-gray-600 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-400",
  },
  [UPLOAD_STATUS.UPLOADING]: {
    label: (p) => (p !== undefined ? `Đang tải lên ${p}%` : "Đang tải lên"),
    className:
      "border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-800 dark:bg-blue-950 dark:text-blue-400",
  },
  [UPLOAD_STATUS.CONFIRMING]: {
    label: () => "Đang xác nhận",
    className:
      "border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-800 dark:bg-blue-950 dark:text-blue-400",
  },
  [UPLOAD_STATUS.SCANNING]: {
    label: () => "Đang quét virus…",
    className:
      "border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-400",
  },
  [UPLOAD_STATUS.DONE]: {
    label: () => "Hoàn thành",
    className:
      "border-green-200 bg-green-50 text-green-700 dark:border-green-800 dark:bg-green-950 dark:text-green-400",
  },
  [UPLOAD_STATUS.ERROR]: {
    label: () => "Lỗi",
    className:
      "border-red-200 bg-red-50 text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-400",
  },
  [UPLOAD_STATUS.CANCELLED]: {
    label: () => "Đã hủy",
    className:
      "border-gray-200 bg-gray-50 text-gray-500 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-500",
  },
  [UPLOAD_STATUS.INFECTED]: {
    label: () => "Phát hiện virus",
    className:
      "border-red-200 bg-red-50 text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-400",
  },
};

export function UploadStatusBadge({
  status,
  progress,
  className,
}: UploadStatusBadgeProps) {
  const config = STATUS_CONFIG[status];
  return (
    <Badge
      variant="outline"
      className={cn("shrink-0 text-xs font-medium", config.className, className)}
    >
      {config.label(progress)}
    </Badge>
  );
}
