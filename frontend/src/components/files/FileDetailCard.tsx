"use client";

import { useState } from "react";
import {
  Download,
  Loader2,
  RotateCcw,
  Share2,
  Eye,
  FileText,
  FileImage,
  FileVideo,
  FileAudio,
  FileArchive,
  File as FileIcon,
  Trash2,
  Pencil,
  MoveRight,
} from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { downloadFile, getPresignedGetUrl, restoreFile } from "@/lib/api/file";
import { FILE_STATUS } from "@/lib/constants";
import type { FileMetadataResponse } from "@/types/file/file";
import { cn } from "@/lib/utils";

const PREVIEWABLE_MIME_TYPES = [
  "application/pdf",
  "text/csv",
  "text/plain",
  "video/mp4",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
];

function isPreviewable(mimeType: string) {
  if (mimeType.startsWith("image/")) return true;
  return PREVIEWABLE_MIME_TYPES.includes(mimeType);
}

// ─── Helpers ─────────────────────────────────────────────────────

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

function formatRelative(iso: string | null): string {
  if (!iso) return "—";
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60000);
  const hours = Math.floor(mins / 60);
  const days = Math.floor(hours / 24);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins} mins ago`;
  if (hours < 24) return `${hours} hours ago`;
  return `${days} days ago`;
}

function renderFileIcon(mimeType: string, size = "h-12 w-12") {
  const cls = cn(size);
  if (mimeType.startsWith("image/")) return <FileImage className={cn(cls, "text-sky-500")} />;
  if (mimeType.startsWith("video/")) return <FileVideo className={cn(cls, "text-purple-500")} />;
  if (mimeType.startsWith("audio/")) return <FileAudio className={cn(cls, "text-orange-500")} />;
  if (mimeType.includes("zip") || mimeType.includes("tar") || mimeType.includes("rar"))
    return <FileArchive className={cn(cls, "text-amber-500")} />;
  if (mimeType.includes("pdf") || mimeType.includes("document") || mimeType.startsWith("text/"))
    return <FileText className={cn(cls, "text-red-500")} />;
  return <FileIcon className={cn(cls, "text-gray-400")} />;
}

function getMimeLabel(mimeType: string): string {
  if (mimeType.includes("pdf")) return "PDF FILE";
  if (mimeType.includes("spreadsheet") || mimeType.includes("excel") || mimeType.includes("xlsx")) return "EXCEL FILE";
  if (mimeType.includes("document") || mimeType.includes("word") || mimeType.includes("docx")) return "WORD FILE";
  if (mimeType.includes("presentation") || mimeType.includes("powerpoint")) return "PRESENTATION FILE";
  if (mimeType.startsWith("image/")) return "IMAGE FILE";
  if (mimeType.startsWith("video/")) return "VIDEO FILE";
  if (mimeType.startsWith("audio/")) return "AUDIO FILE";
  if (mimeType.includes("zip") || mimeType.includes("tar")) return "ARCHIVE FILE";
  const parts = mimeType.split("/");
  return (parts[1] ?? mimeType).toUpperCase() + " FILE";
}

// ─── Props ───────────────────────────────────────────────────────

interface FileDetailCardProps {
  file: FileMetadataResponse | null;
  isLoading?: boolean;
  /** "default" = All Files view (Download/Share/Preview), "trash" = Trash view (Delete/Restore) */
  mode?: "default" | "trash";
  /** Called when a mutation (restore/delete) succeeds so parent can refresh data */
  onMutate?: () => void;
  onDelete?: (file: FileMetadataResponse) => void;
  onMove?: (file: FileMetadataResponse) => void;
}

// ─── Component ───────────────────────────────────────────────────

export function FileDetailCard({
  file,
  isLoading,
  mode = "default",
  onMutate,
  onDelete,
  onMove,
}: FileDetailCardProps) {
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

  // ── Loading skeleton ──
  if (isLoading) {
    return (
      <div className="flex flex-col h-full p-5 space-y-4">
        <div className="flex items-center justify-between">
          <Skeleton className="h-5 w-28" />
        </div>
        <Skeleton className="h-36 w-full rounded-xl" />
        <Skeleton className="h-5 w-40" />
        <Skeleton className="h-4 w-20" />
        <div className="grid grid-cols-2 gap-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="space-y-1">
              <Skeleton className="h-3 w-14" />
              <Skeleton className="h-4 w-20" />
            </div>
          ))}
        </div>
      </div>
    );
  }

  // ── Empty state ──
  if (!file) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 p-8 text-center">
        <FileIcon className="h-12 w-12 text-gray-200" />
        <p className="text-sm text-gray-400">Select a file to see details</p>
      </div>
    );
  }

  const activityItems = [
    {
      actor: file.uploadedBy?.fullName ?? "Unknown",
      action: "uploaded this file",
      time: formatRelative(file.createdAt),
      initial: (file.uploadedBy?.fullName ?? "U").charAt(0),
    },
    ...(file.updatedAt && file.updatedAt !== file.createdAt
      ? [{ actor: "System", action: "updated this file", time: formatRelative(file.updatedAt), initial: "S" }]
      : []),
  ];

  return (
    <div className="flex flex-col h-full overflow-y-auto">
      {/* ── Header ── */}
      <div className="flex items-center px-5 pt-5 pb-4 border-b border-gray-100">
        <div className="flex items-center gap-2 text-sm font-semibold text-gray-700">
          <span className="text-gray-400 text-base">ℹ</span>
          File Details
        </div>
      </div>

      <div className="flex flex-col gap-4 px-5 py-4">
        {/* ── Thumbnail ── */}
        <div className="flex h-36 items-center justify-center rounded-xl bg-gray-50 border border-gray-100">
          {renderFileIcon(file.mimeType, "h-14 w-14")}
        </div>

        {/* ── File name & type ── */}
        <div>
          <p className="font-semibold text-gray-900 break-all leading-snug">{file.originalName}</p>
          <p className="mt-1 text-xs font-medium tracking-wider text-gray-400 uppercase">
            {getMimeLabel(file.mimeType)}
          </p>
        </div>

        {/* ── Metadata grid ── */}
        <div className="grid grid-cols-2 gap-x-4 gap-y-3">
          <MetaCell label="SIZE" value={formatBytes(file.sizeBytes)} />
          <MetaCell label="MODIFIED" value={formatRelative(file.updatedAt)} />
          <MetaCell label="OWNER" value={file.uploadedBy?.fullName ?? "—"} />
          <MetaCell label="LOCATION" value="My Files" />
        </div>

        {/* ── Activity ── */}
        <div>
          <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700">
            <RotateCcw className="h-3.5 w-3.5 text-gray-400" />
            Activity
          </div>
          <div className="space-y-3">
            {activityItems.map((item, i) => (
              <div key={i} className="flex items-start gap-2.5">
                <div className="mt-0.5 h-6 w-6 shrink-0 rounded-full bg-gradient-to-br from-[#7C3AED] to-[#a855f7] flex items-center justify-center text-white text-xs font-bold">
                  {item.initial}
                </div>
                <div className="min-w-0">
                  <p className="text-xs text-gray-600 leading-snug">
                    <span className="font-semibold text-gray-800">{item.actor}</span>{" "}
                    {item.action}
                  </p>
                  <p className="text-xs text-gray-400 mt-0.5">{item.time}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* ── Action buttons ── */}
      <div className="mt-auto border-t border-gray-100 px-5 py-4 space-y-2">
        {mode === "default" && (
          <>
            <div className="grid grid-cols-2 gap-2">
              <Button
                size="sm"
                variant="outline"
                className="gap-2 w-full"
                disabled={actionLoading !== null}
                onClick={handleDownload}
              >
                {actionLoading === "download"
                  ? <Loader2 className="h-4 w-4 animate-spin" />
                  : <Download className="h-4 w-4" />}
                Download
              </Button>
              <Button
                size="sm"
                variant="outline"
                className="gap-2 w-full"
                disabled
              >
                <Share2 className="h-4 w-4" />
                Share
              </Button>
            </div>
            {isPreviewable(file.mimeType) && (
              <Button
                size="sm"
                variant="outline"
                className="gap-2 w-full"
                disabled={actionLoading !== null}
                onClick={handlePreview}
              >
                {actionLoading === "preview"
                  ? <Loader2 className="h-4 w-4 animate-spin" />
                  : <Eye className="h-4 w-4" />}
                Preview
              </Button>
            )}
            <div className="grid grid-cols-2 gap-2">
              <Button
                size="sm"
                variant="outline"
                className="gap-2 w-full border-red-200 text-red-600 hover:bg-red-50 hover:text-red-700"
                disabled={actionLoading !== null}
                onClick={() => onDelete?.(file)}
              >
                <Trash2 className="h-4 w-4" />
                Remove
              </Button>
              <Button
                size="sm"
                variant="outline"
                className="gap-2 w-full"
                disabled={actionLoading !== null}
                onClick={() => onMove?.(file)}
              >
                <MoveRight className="h-4 w-4" />
                Move
              </Button>
            </div>
          </>
        )}

        {mode === "trash" && (
          <div className="grid grid-cols-2 gap-2">
            <Button
              size="sm"
              variant="outline"
              className="gap-2 w-full border-red-200 text-red-600 hover:bg-red-50 hover:text-red-700"
              disabled={actionLoading !== null}
              onClick={() => onDelete?.(file)}
            >
              {actionLoading === "delete"
                ? <Loader2 className="h-4 w-4 animate-spin" />
                : <Trash2 className="h-4 w-4" />}
              Delete
            </Button>
            <Button
              size="sm"
              variant="outline"
              className="gap-2 w-full border-[#7C3AED]/30 text-[#7C3AED] hover:bg-[#F3E8FF] hover:text-[#7C3AED]"
              disabled={actionLoading !== null}
              onClick={handleRestore}
            >
              {actionLoading === "restore"
                ? <Loader2 className="h-4 w-4 animate-spin" />
                : <RotateCcw className="h-4 w-4" />}
              Restore
            </Button>
          </div>
        )}
      </div>

    </div>
  );
}

// ─── Helper sub-components ────────────────────────────────────────

function MetaCell({ label, value }: { label: string; value: string }) {
  return (
    <div className="space-y-0.5">
      <p className="text-[10px] font-semibold tracking-wider text-gray-400 uppercase">{label}</p>
      <p className="text-sm font-medium text-gray-800 truncate">{value}</p>
    </div>
  );
}
