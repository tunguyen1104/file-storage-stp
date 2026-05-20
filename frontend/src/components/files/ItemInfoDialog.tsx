"use client";

import { useEffect, useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Folder as FolderIcon,
  FileImage,
  FileText,
  FileArchive,
  FileVideo,
  FileAudio,
  File as FileIcon,
  Calendar,
  HardDrive,
  User,
  Shield,
  Clock,
  ChevronRight,
  AlertCircle,
  FileCheck,
  Tag,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { getFolderDetails } from "@/lib/api/folder";
import { getFileMetadata } from "@/lib/api/file";
import type { FolderMetadataResponse } from "@/types/folder/folder";
import type { FileMetadataResponse } from "@/types/file/file";

// ─── Helpers ─────────────────────────────────────────────────────

function formatBytes(bytes: number): string {
  if (bytes === 0) return "0 B";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatRelative(iso: string | null | undefined): string {
  if (!iso) return "—";
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60000);
  const hours = Math.floor(mins / 60);
  const days = Math.floor(hours / 24);
  if (mins < 1) return "Vừa xong";
  if (mins < 60) return `${mins} phút trước`;
  if (hours < 24) return `${hours} giờ trước`;
  if (days < 30) return `${days} ngày trước`;
  return formatDate(iso);
}

function renderFolderIcon(size = "h-10 w-10") {
  return <FolderIcon className={cn(size, "text-blue-500 fill-blue-100")} />;
}

function renderFileIconForMime(mimeType: string, size = "h-10 w-10") {
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
  if (mimeType.includes("pdf")) return "PDF";
  if (mimeType.includes("spreadsheet") || mimeType.includes("excel") || mimeType.includes("xlsx")) return "Excel";
  if (mimeType.includes("document") || mimeType.includes("word") || mimeType.includes("docx")) return "Word";
  if (mimeType.includes("presentation") || mimeType.includes("powerpoint")) return "PowerPoint";
  if (mimeType.startsWith("image/")) return "Ảnh";
  if (mimeType.startsWith("video/")) return "Video";
  if (mimeType.startsWith("audio/")) return "Âm thanh";
  if (mimeType.includes("zip") || mimeType.includes("tar")) return "Lưu trữ";
  return mimeType.split("/")[1]?.toUpperCase() ?? "Tệp tin";
}

function getFileStatusLabel(status: string): { label: string; color: string } {
  switch (status) {
    case "ACTIVE":
      return { label: "Đang hoạt động", color: "text-emerald-600 bg-emerald-50 border-emerald-200" };
    case "PENDING":
      return { label: "Đang chờ", color: "text-amber-600 bg-amber-50 border-amber-200" };
    case "PENDING_DELETE":
      return { label: "Chờ xóa", color: "text-red-600 bg-red-50 border-red-200" };
    case "DELETED":
      return { label: "Đã xóa", color: "text-gray-500 bg-gray-50 border-gray-200" };
    case "UPLOAD_FAILED":
      return { label: "Tải lên thất bại", color: "text-red-600 bg-red-50 border-red-200" };
    default:
      return { label: status, color: "text-gray-500 bg-gray-50 border-gray-200" };
  }
}

// ─── Props ───────────────────────────────────────────────────────

interface ItemInfoDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  itemId: string | null;
  itemType: "folder" | "file";
}

// ─── Component ───────────────────────────────────────────────────

export function ItemInfoDialog({ open, onOpenChange, itemId, itemType }: ItemInfoDialogProps) {
  const [folderData, setFolderData] = useState<FolderMetadataResponse | null>(null);
  const [fileData, setFileData] = useState<FileMetadataResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open || !itemId) return;

    setIsLoading(true);
    setError(null);
    setFolderData(null);
    setFileData(null);

    if (itemType === "folder") {
      getFolderDetails(itemId)
        .then(setFolderData)
        .catch((err) => setError(err instanceof Error ? err.message : "Không thể tải thông tin thư mục"))
        .finally(() => setIsLoading(false));
    } else {
      getFileMetadata(itemId)
        .then(setFileData)
        .catch((err) => setError(err instanceof Error ? err.message : "Không thể tải thông tin tệp"))
        .finally(() => setIsLoading(false));
    }
  }, [open, itemId, itemType]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md p-0 overflow-hidden rounded-2xl">
        {/* ── Gradient Header ── */}
        <div
          className={cn(
            "px-6 pt-6 pb-5",
            itemType === "folder"
              ? "bg-gradient-to-br from-blue-50 via-indigo-50 to-purple-50"
              : "bg-gradient-to-br from-violet-50 via-purple-50 to-fuchsia-50"
          )}
        >
          <DialogHeader className="gap-3">
            <div className="flex items-center gap-3">
              <div
                className={cn(
                  "flex h-12 w-12 items-center justify-center rounded-xl shadow-sm",
                  itemType === "folder"
                    ? "bg-white/80 border border-blue-100"
                    : "bg-white/80 border border-purple-100"
                )}
              >
                {isLoading ? (
                  <Skeleton className="h-7 w-7 rounded" />
                ) : itemType === "folder" ? (
                  renderFolderIcon("h-7 w-7")
                ) : fileData ? (
                  renderFileIconForMime(fileData.mimeType, "h-7 w-7")
                ) : (
                  <FileIcon className="h-7 w-7 text-gray-300" />
                )}
              </div>
              <div className="min-w-0 flex-1">
                <DialogTitle className="text-base font-semibold text-gray-900 truncate">
                  {isLoading ? (
                    <Skeleton className="h-5 w-40" />
                  ) : error ? (
                    "Lỗi"
                  ) : itemType === "folder" ? (
                    folderData?.name ?? "—"
                  ) : (
                    fileData?.originalName ?? "—"
                  )}
                </DialogTitle>
                <DialogDescription className="text-xs text-gray-500 mt-0.5">
                  {itemType === "folder" ? "Thông tin thư mục" : "Thông tin tệp"}
                </DialogDescription>
              </div>
            </div>
          </DialogHeader>
        </div>

        {/* ── Body ── */}
        <div className="px-6 pb-6 space-y-5">
          {/* Error State */}
          {error && (
            <div className="flex items-center gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600">
              <AlertCircle className="h-4 w-4 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {/* Loading State */}
          {isLoading && (
            <div className="space-y-4 pt-1">
              {Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="flex items-center gap-3">
                  <Skeleton className="h-4 w-4 rounded" />
                  <div className="flex-1 space-y-1.5">
                    <Skeleton className="h-3 w-16" />
                    <Skeleton className="h-4 w-32" />
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* ── Folder Info ── */}
          {!isLoading && !error && itemType === "folder" && folderData && (
            <>
              {/* Metadata Grid */}
              <div className="space-y-3">
                <MetaRow
                  icon={<HardDrive className="h-4 w-4 text-blue-500" />}
                  label="Kích thước"
                  value={formatBytes(folderData.sizeBytes)}
                />
                <MetaRow
                  icon={<Calendar className="h-4 w-4 text-green-500" />}
                  label="Ngày tạo"
                  value={formatDate(folderData.createdAt)}
                  sub={formatRelative(folderData.createdAt)}
                />
                <MetaRow
                  icon={<Clock className="h-4 w-4 text-amber-500" />}
                  label="Cập nhật lần cuối"
                  value={formatDate(folderData.updatedAt)}
                  sub={formatRelative(folderData.updatedAt)}
                />

              </div>

              {/* Breadcrumb path */}
              {folderData.breadcrumb && folderData.breadcrumb.length > 0 && (
                <div className="space-y-2">
                  <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">
                    Đường dẫn
                  </p>
                  <div className="flex items-center flex-wrap gap-1 rounded-lg bg-gray-50 border border-gray-100 px-3 py-2.5">
                    {folderData.breadcrumb.map((crumb, i) => (
                      <span key={crumb.id} className="flex items-center gap-1">
                        {i > 0 && (
                          <ChevronRight className="h-3 w-3 text-gray-300 shrink-0" />
                        )}
                        <span
                          className={cn(
                            "text-xs px-1.5 py-0.5 rounded",
                            i === folderData.breadcrumb!.length - 1
                              ? "font-semibold text-[#7C3AED] bg-[#F3E8FF]"
                              : "text-gray-600"
                          )}
                        >
                          {crumb.name === "root" ? "Gốc" : crumb.name}
                        </span>
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}

          {/* ── File Info ── */}
          {!isLoading && !error && itemType === "file" && fileData && (
            <>
              {/* Status badge */}
              <div className="flex items-center gap-2">
                <span
                  className={cn(
                    "inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium",
                    getFileStatusLabel(fileData.status).color
                  )}
                >
                  <span className="h-1.5 w-1.5 rounded-full bg-current" />
                  {getFileStatusLabel(fileData.status).label}
                </span>
                <span className="inline-flex items-center gap-1 rounded-full border border-gray-200 bg-gray-50 px-2.5 py-1 text-[11px] font-medium text-gray-500">
                  <Tag className="h-3 w-3" />
                  {getMimeLabel(fileData.mimeType)}
                </span>
              </div>

              {/* Metadata Grid */}
              <div className="space-y-3">
                <MetaRow
                  icon={<HardDrive className="h-4 w-4 text-blue-500" />}
                  label="Kích thước"
                  value={formatBytes(fileData.sizeBytes)}
                />
                <MetaRow
                  icon={<FileCheck className="h-4 w-4 text-teal-500" />}
                  label="Loại tệp (MIME)"
                  value={fileData.mimeType}
                />
                <MetaRow
                  icon={<User className="h-4 w-4 text-indigo-500" />}
                  label="Người tải lên"
                  value={fileData.uploadedBy?.fullName ?? "—"}
                />
                <MetaRow
                  icon={<Calendar className="h-4 w-4 text-green-500" />}
                  label="Ngày tạo"
                  value={formatDate(fileData.createdAt)}
                  sub={formatRelative(fileData.createdAt)}
                />
                <MetaRow
                  icon={<Clock className="h-4 w-4 text-amber-500" />}
                  label="Cập nhật lần cuối"
                  value={formatDate(fileData.updatedAt)}
                  sub={formatRelative(fileData.updatedAt)}
                />
                {fileData.checksumMd5 && (
                  <MetaRow
                    icon={<Shield className="h-4 w-4 text-cyan-500" />}
                    label="Checksum MD5"
                    value={fileData.checksumMd5}
                    mono
                  />
                )}

                {fileData.deletedAt && (
                  <MetaRow
                    icon={<AlertCircle className="h-4 w-4 text-red-500" />}
                    label="Ngày xóa"
                    value={formatDate(fileData.deletedAt)}
                    sub={formatRelative(fileData.deletedAt)}
                  />
                )}
                {fileData.scheduledDeleteAt && (
                  <MetaRow
                    icon={<AlertCircle className="h-4 w-4 text-red-400" />}
                    label="Xóa vĩnh viễn lúc"
                    value={formatDate(fileData.scheduledDeleteAt)}
                  />
                )}
              </div>
            </>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}

// ─── MetaRow sub-component ────────────────────────────────────────

function MetaRow({
  icon,
  label,
  value,
  sub,
  mono,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  sub?: string;
  mono?: boolean;
}) {
  return (
    <div className="flex items-start gap-3 group">
      <div className="mt-0.5 shrink-0 flex h-8 w-8 items-center justify-center rounded-lg bg-gray-50 border border-gray-100 group-hover:bg-white group-hover:shadow-sm transition-all">
        {icon}
      </div>
      <div className="min-w-0 flex-1">
        <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">{label}</p>
        <p
          className={cn(
            "text-sm text-gray-800 break-all leading-snug mt-0.5",
            mono && "font-mono text-xs text-gray-600"
          )}
          title={value}
        >
          {value}
        </p>
        {sub && <p className="text-[11px] text-gray-400 mt-0.5">{sub}</p>}
      </div>
    </div>
  );
}
