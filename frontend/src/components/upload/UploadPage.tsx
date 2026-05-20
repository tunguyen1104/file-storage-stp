"use client";

import { useState, useEffect } from "react";
import { useUpload } from "@/contexts/upload-context";
import { UploadDropZone } from "@/components/upload/UploadDropZone";
import { UploadQueuePanel } from "@/components/upload/UploadQueuePanel";
import { InterruptedUploadBanner } from "@/components/upload/InterruptedUploadBanner";
import type { PendingMultipartRecord } from "@/types/upload/upload.types";

export function UploadPage() {
  const {
    uploadQueue,
    addFiles,
    cancelUpload,
    retryUpload,
    resumeUpload,
    clearDone,
    clearErrors,
    checkInterruptedUploads,
    resumeInterruptedUpload,
    discardInterruptedUpload,
  } = useUpload();

  const [interrupted, setInterrupted] = useState<PendingMultipartRecord[]>([]);

  useEffect(() => {
    checkInterruptedUploads().then(setInterrupted);
  }, [checkInterruptedUploads]);

  return (
    <div className="flex flex-col gap-6 p-6 max-w-4xl mx-auto">
      {/* Page header */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Tải lên tệp tin</h1>
        <p className="text-muted-foreground mt-1 text-sm">
          Kéo thả tệp tin hoặc click để chọn. Tệp tin được quét virus trước khi kích hoạt.
          Tệp tin lớn (&ge;100 MB) sử dụng cơ chế tải lên phân đoạn có thể tiếp tục.
        </p>
      </div>

      {/* Interrupted upload banners (after page refresh with unfinished multipart) */}
      {interrupted.map((record) => (
        <InterruptedUploadBanner
          key={record.uploadId}
          record={record}
          onResume={(rec, file) => {
            resumeInterruptedUpload(rec, file);
            setInterrupted((prev) => prev.filter((r) => r.uploadId !== rec.uploadId));
          }}
          onDiscard={() => {
            discardInterruptedUpload(record.uploadId);
            setInterrupted((prev) =>
              prev.filter((r) => r.uploadId !== record.uploadId)
            );
          }}
        />
      ))}

      {/* Drop zone */}
      <UploadDropZone onFilesSelected={addFiles} />

      {/* Queue panel — shown only when there are items */}
      {uploadQueue.length > 0 && (
        <UploadQueuePanel
          items={uploadQueue}
          onCancel={cancelUpload}
          onRetry={retryUpload}
          onResume={resumeUpload}
          onClearDone={clearDone}
          onClearErrors={clearErrors}
        />
      )}
    </div>
  );
}
