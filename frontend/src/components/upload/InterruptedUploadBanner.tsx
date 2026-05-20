"use client";

import { useRef } from "react";
import { AlertTriangle } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { formatBytes } from "@/lib/utils/upload-utils";
import type { PendingMultipartRecord } from "@/types/upload/upload.types";

interface InterruptedUploadBannerProps {
  record: PendingMultipartRecord;
  onResume: (record: PendingMultipartRecord, file: File) => void;
  onDiscard: () => void;
}

export function InterruptedUploadBanner({
  record,
  onResume,
  onDiscard,
}: InterruptedUploadBannerProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);

  function handleFileSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    // Validate that the re-selected file matches the interrupted upload
    if (file.name !== record.fileName || file.size !== record.fileSizeBytes) {
      toast.error(
        "Tệp tin không khớp. Vui lòng chọn tệp tin gốc để tiếp tục tải lên."
      );
      e.target.value = "";
      return;
    }
    onResume(record, file);
  }

  return (
    <Card className="border-amber-500/50 bg-amber-50/50 dark:bg-amber-950/20">
      <CardContent className="flex items-start gap-3 p-4">
        <AlertTriangle className="h-5 w-5 text-amber-500 mt-0.5 shrink-0" />
        <div className="flex-1 min-w-0">
          <p className="text-sm font-medium text-foreground">Quá trình tải lên bị gián đoạn</p>
          <p
            className="text-xs text-muted-foreground mt-0.5 truncate"
            title={record.fileName}
          >
            {record.fileName}
          </p>
          <p className="text-xs text-muted-foreground">
            {formatBytes(record.fileSizeBytes)}
          </p>
          <p className="text-xs text-muted-foreground mt-1">
            Chọn lại tệp tin để tiếp tục từ vị trí đã dừng.
          </p>
          <div className="flex gap-2 mt-3">
            <Button
              size="sm"
              onClick={() => fileInputRef.current?.click()}
            >
              Chọn tệp để tiếp tục
            </Button>
            <Button size="sm" variant="ghost" onClick={onDiscard}>
              Bỏ qua
            </Button>
          </div>
          <input
            ref={fileInputRef}
            type="file"
            hidden
            onChange={handleFileSelect}
          />
        </div>
      </CardContent>
    </Card>
  );
}
