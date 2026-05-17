"use client";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { UploadQueueItem } from "@/components/upload/UploadQueueItem";
import type { UploadItem } from "@/types/upload/upload.types";
import { UPLOAD_STATUS, UPLOAD_FAILURE_STATUSES } from "@/lib/constants";

interface UploadQueuePanelProps {
  items: UploadItem[];
  onCancel: (itemId: string) => void;
  onRetry: (itemId: string) => void;
  onResume: (itemId: string) => void;
  onClearDone: () => void;
  onClearErrors: () => void;
}

export function UploadQueuePanel({
  items,
  onCancel,
  onRetry,
  onResume,
  onClearDone,
  onClearErrors,
}: UploadQueuePanelProps) {
  const doneCount = items.filter((i) => i.status === UPLOAD_STATUS.DONE).length;
  const errorCount = items.filter((i) => UPLOAD_FAILURE_STATUSES.includes(i.status)).length;

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between pb-3">
        <CardTitle className="text-base font-semibold">
          Upload Queue ({items.length})
        </CardTitle>
        {doneCount > 0 && (
          <Button variant="ghost" size="sm" onClick={onClearDone} className="text-xs">
            Clear completed
          </Button>
        )}
        {errorCount > 0 && (
          <Button variant="ghost" size="sm" onClick={onClearErrors} className="text-xs text-destructive hover:text-destructive hover:bg-destructive/10">
            Clear failed
          </Button>
        )}
      </CardHeader>
      <CardContent className="p-0 pb-2">
        <ScrollArea className="max-h-[500px] px-4">
          <div className="flex flex-col gap-2 pb-2">
            {items.map((item) => (
              <UploadQueueItem
                key={item.id}
                item={item}
                onCancel={() => onCancel(item.id)}
                onRetry={() => onRetry(item.id)}
                onResume={() => onResume(item.id)}
              />
            ))}
          </div>
        </ScrollArea>
      </CardContent>
    </Card>
  );
}
