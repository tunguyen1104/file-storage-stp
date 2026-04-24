"use client";

import { useState } from "react";
import {
  Download,
  Eye,
  Loader2,
  RotateCcw,
  Calendar,
  HardDrive,
  User,
  Hash,
  FileType,
} from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { downloadFile, getPresignedGetUrl, restoreFile } from "@/lib/api/file";
import { FILE_STATUS } from "@/lib/constants";
import type { FileMetadataResponse } from "@/types/file/file";

// ─── Helpers ─────────────────────────────────────────────────────

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

function formatDateTime(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString();
}

const STATUS_STYLES: Record<string, string> = {
  [FILE_STATUS.ACTIVE]: "border-green-200 bg-green-50 text-green-700 dark:border-green-800 dark:bg-green-950 dark:text-green-400",
  [FILE_STATUS.PENDING]: "border-yellow-200 bg-yellow-50 text-yellow-700 dark:border-yellow-800 dark:bg-yellow-950 dark:text-yellow-400",
  [FILE_STATUS.PENDING_DELETE]: "border-orange-200 bg-orange-50 text-orange-700 dark:border-orange-800 dark:bg-orange-950 dark:text-orange-400",
  [FILE_STATUS.DELETED]: "border-red-200 bg-red-50 text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-400",
  [FILE_STATUS.UPLOAD_FAILED]: "border-red-200 bg-red-50 text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-400",
};

function statusLabel(status: string): string {
  return status.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

// ─── Props ───────────────────────────────────────────────────────

interface FileDetailCardProps {
  file: FileMetadataResponse | null;
  isLoading?: boolean;
  /** Called when a mutation (restore) succeeds so parent can refresh data */
  onMutate?: () => void;
}

// ─── Component ───────────────────────────────────────────────────

export function FileDetailCard({ file, isLoading, onMutate }: FileDetailCardProps) {
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  async function handlePreview() {
    if (!file) return;
    setActionLoading("preview");
    try {
      const { getUrl } = await getPresignedGetUrl(file.id);
      window.open(getUrl, "_blank", "noopener,noreferrer");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to get preview URL");
    } finally {
      setActionLoading(null);
    }
  }

  async function handleDownload() {
    if (!file) return;
    setActionLoading("download");
    try {
      const { downloadUrl } = await downloadFile(file.id);
      // Trigger browser download via hidden anchor
      const a = document.createElement("a");
      a.href = downloadUrl;
      a.download = file.originalName;
      a.rel = "noopener noreferrer";
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to download file");
    } finally {
      setActionLoading(null);
    }
  }

  async function handleRestore() {
    if (!file) return;
    setActionLoading("restore");
    try {
      await restoreFile(file.id);
      toast.success(`"${file.originalName}" has been restored`);
      onMutate?.();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to restore file");
    } finally {
      setActionLoading(null);
    }
  }

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <Skeleton className="h-6 w-48" />
        </CardHeader>
        <CardContent className="space-y-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="flex items-center gap-3">
              <Skeleton className="h-4 w-4 rounded" />
              <Skeleton className="h-4 w-32" />
              <Skeleton className="ml-auto h-4 w-24" />
            </div>
          ))}
        </CardContent>
      </Card>
    );
  }

  if (!file) {
    return (
      <Card>
        <CardContent className="py-12 text-center text-muted-foreground">
          No file selected.
        </CardContent>
      </Card>
    );
  }

  const isActive = file.status === FILE_STATUS.ACTIVE;
  const isRestorable =
    file.status === FILE_STATUS.PENDING_DELETE || file.status === FILE_STATUS.DELETED;

  const detailRows: { icon: React.ReactNode; label: string; value: React.ReactNode }[] = [
    { icon: <FileType className="h-4 w-4" />, label: "MIME Type", value: file.mimeType },
    { icon: <HardDrive className="h-4 w-4" />, label: "Size", value: formatBytes(file.sizeBytes) },
    { icon: <User className="h-4 w-4" />, label: "Uploaded By", value: file.uploadedBy?.fullName ?? "—" },
    { icon: <Calendar className="h-4 w-4" />, label: "Created", value: formatDateTime(file.createdAt) },
    { icon: <Calendar className="h-4 w-4" />, label: "Updated", value: formatDateTime(file.updatedAt) },
    { icon: <Hash className="h-4 w-4" />, label: "MD5 Checksum", value: file.checksumMd5 ?? "—" },
  ];

  if (file.deletedAt) {
    detailRows.push({ icon: <Calendar className="h-4 w-4" />, label: "Deleted At", value: formatDateTime(file.deletedAt) });
  }
  if (file.scheduledDeleteAt) {
    detailRows.push({ icon: <Calendar className="h-4 w-4" />, label: "Permanent Delete", value: formatDateTime(file.scheduledDeleteAt) });
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between gap-4">
        <div className="space-y-1.5 min-w-0">
          <CardTitle className="truncate text-lg">{file.originalName}</CardTitle>
          <Badge variant="outline" className={STATUS_STYLES[file.status] ?? ""}>
            {statusLabel(file.status)}
          </Badge>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          {isActive && (
            <>
              <Button
                size="sm"
                variant="outline"
                className="gap-1.5"
                disabled={actionLoading !== null}
                onClick={handlePreview}
              >
                {actionLoading === "preview"
                  ? <Loader2 className="h-4 w-4 animate-spin" />
                  : <Eye className="h-4 w-4" />}
                Preview
              </Button>
              <Button
                size="sm"
                className="gap-1.5"
                disabled={actionLoading !== null}
                onClick={handleDownload}
              >
                {actionLoading === "download"
                  ? <Loader2 className="h-4 w-4 animate-spin" />
                  : <Download className="h-4 w-4" />}
                Download
              </Button>
            </>
          )}
          {isRestorable && (
            <Button
              size="sm"
              variant="outline"
              className="gap-1.5"
              disabled={actionLoading !== null}
              onClick={handleRestore}
            >
              {actionLoading === "restore"
                ? <Loader2 className="h-4 w-4 animate-spin" />
                : <RotateCcw className="h-4 w-4" />}
              Restore
            </Button>
          )}
        </div>
      </CardHeader>
      <Separator />
      <CardContent className="pt-4">
        <dl className="space-y-3">
          {detailRows.map((row) => (
            <div key={row.label} className="flex items-center gap-3 text-sm">
              <span className="text-muted-foreground shrink-0">{row.icon}</span>
              <dt className="text-muted-foreground w-32 shrink-0">{row.label}</dt>
              <dd className="truncate font-medium">{row.value}</dd>
            </div>
          ))}
        </dl>
      </CardContent>
    </Card>
  );
}
