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
        "File does not match. Please select the original file to resume."
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
          <p className="text-sm font-medium text-foreground">Upload interrupted</p>
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
            Re-select the file to resume from where it left off.
          </p>
          <div className="flex gap-2 mt-3">
            <Button
              size="sm"
              onClick={() => fileInputRef.current?.click()}
            >
              Select file to resume
            </Button>
            <Button size="sm" variant="ghost" onClick={onDiscard}>
              Discard
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
