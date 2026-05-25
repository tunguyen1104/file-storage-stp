"use client";

import { useState, useEffect, useCallback } from "react";
import useSWR from "swr";
import {
  AlertCircle,
  RefreshCw,
  Search,
  FolderOpen,
  FileImage,
  FileText,
  FileArchive,
  FileVideo,
  FileAudio,
  File as FileIcon,
  ChevronRight,
  Home,
  X,
  Folder as FolderIcon,
  ShieldAlert,
  ArrowUp,
  ArrowDown,
  MoreVertical,
  Download,
  Pencil,
  Trash2,
  RotateCcw,
  MoveRight,
  Eye,
  Info,
  Share2,
  Link,
} from "lucide-react";
import { toast } from "sonner";

import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { FileDetailCard } from "@/components/files/FileDetailCard";
import { getAdminFolderItems, getFolderBreadcrumbs } from "@/lib/api/folder";
import { useDebounce } from "@/hooks/use-debounce";
import { AdminFileTable } from "./AdminFileTable";
import { DEFAULT_PAGE_SIZE, FILE_SORT_BY, SORT_DIRECTION } from "@/lib/constants";
import type { FileMetadataResponse } from "@/types/file/file";
import { cn } from "@/lib/utils";

import { DeleteFileDialog } from "@/components/files/DeleteFileDialog";
import { RenameFileDialog } from "@/components/files/RenameFileDialog";
import { RenameFolderDialog } from "@/components/folders/RenameFolderDialog";
import { DeleteFolderDialog } from "@/components/folders/DeleteFolderDialog";
import { MoveItemDialog } from "@/components/files/MoveItemDialog";
import { FilePreviewDialog } from "@/components/files/FilePreviewDialog";
import { ShareItemDialog } from "@/components/files/ShareItemDialog";
import { downloadFile, getPresignedGetUrl, restoreFile } from "@/lib/api/file";
import { useAuth } from "@/hooks/use-auth";
import { listTenants } from "@/lib/api/tenants";

// ─── File icon helpers ───────────────────────────────────────────

export function renderFileIcon(mimeType: string | null, size = "h-5 w-5") {
  if (!mimeType) return <FolderIcon className={cn(size, "text-blue-500 fill-blue-100")} />;
  if (mimeType.startsWith("image/")) return <FileImage className={cn(size, "text-sky-500")} />;
  if (mimeType.startsWith("video/")) return <FileVideo className={cn(size, "text-purple-500")} />;
  if (mimeType.startsWith("audio/")) return <FileAudio className={cn(size, "text-orange-500")} />;
  if (mimeType.includes("zip") || mimeType.includes("tar") || mimeType.includes("rar"))
    return <FileArchive className={cn(size, "text-amber-500")} />;
  if (mimeType.includes("pdf") || mimeType.includes("document") || mimeType.startsWith("text/"))
    return <FileText className={cn(size, "text-blue-500")} />;
  return <FileIcon className={cn(size, "text-gray-400")} />;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

function isFilePreviewable(fileName: string | null, mimeType: string | null): boolean {
  if (!fileName) return false;
  const ext = fileName.split(".").pop()?.toLowerCase();
  if (!ext) return false;
  
  const PREVIEWABLE_EXTENSIONS = [
    "pdf",
    "png", "jpg", "jpeg", "gif", "webp", "svg",
    "mp4", "webm",
    "mp3", "ogg", "wav",
    "txt"
  ];
  
  if (!PREVIEWABLE_EXTENSIONS.includes(ext)) {
    return false;
  }
  
  if (mimeType) {
    if (mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType.startsWith("audio/")) {
      return true;
    }
    if (mimeType === "application/pdf" || mimeType === "text/plain") {
      return true;
    }
  }
  
  return PREVIEWABLE_EXTENSIONS.includes(ext);
}

export function getTypeName(mimeType: string | null): string {
  if (!mimeType) return "Thư mục";
  if (mimeType.includes("pdf")) return "PDF";
  if (mimeType.includes("spreadsheet") || mimeType.includes("excel") || mimeType.includes("xlsx")) return "Excel";
  if (mimeType.includes("document") || mimeType.includes("word") || mimeType.includes("docx")) return "Word";
  if (mimeType.includes("presentation") || mimeType.includes("powerpoint")) return "PowerPoint";
  if (mimeType.startsWith("image/")) return "Ảnh";
  if (mimeType.startsWith("video/")) return "Video";
  if (mimeType.startsWith("audio/")) return "Âm thanh";
  if (mimeType.includes("zip") || mimeType.includes("tar")) return "Lưu trữ";
  if (mimeType === "application/octet-stream") return "Nhị phân";
  return mimeType.split("/")[1]?.toUpperCase() ?? "Tệp tin";
}

const PREVIEWABLE_MIME_TYPES = [
  "application/pdf",
  "text/csv",
  "text/plain",
  "video/mp4",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
];

function isPreviewable(mimeType: string | null) {
  if (!mimeType) return false;
  if (mimeType.startsWith("image/")) return true;
  return PREVIEWABLE_MIME_TYPES.includes(mimeType);
}

export const getStatusBadge = (status: string) => {
  switch (status) {
    case "ACTIVE":
      return (
        <Badge variant="outline" className="bg-emerald-50 text-emerald-700 border-emerald-200 font-medium px-2.5 py-0.5 rounded-full whitespace-nowrap">
          Hoạt động
        </Badge>
      );
    case "PENDING_DELETE":
      return (
        <Badge variant="outline" className="bg-rose-50 text-rose-700 border-rose-200 font-medium px-2.5 py-0.5 rounded-full whitespace-nowrap">
          Chờ xóa
        </Badge>
      );
    case "PENDING":
      return (
        <Badge variant="outline" className="bg-amber-50 text-amber-700 border-amber-200 font-medium px-2.5 py-0.5 rounded-full whitespace-nowrap">
          Đang chờ
        </Badge>
      );
    case "SCANNING":
      return (
        <Badge variant="outline" className="bg-blue-50 text-blue-700 border-blue-200 font-medium px-2.5 py-0.5 rounded-full whitespace-nowrap">
          Đang quét
        </Badge>
      );
    case "INFECTED":
      return (
        <Badge variant="outline" className="bg-red-100 text-red-800 border-red-200 font-bold px-2.5 py-0.5 rounded-full whitespace-nowrap">
          Nhiễm virus
        </Badge>
      );
    case "UPLOAD_FAILED":
      return (
        <Badge variant="outline" className="bg-red-50 text-red-600 border-red-200 font-medium px-2.5 py-0.5 rounded-full whitespace-nowrap">
          Thất bại
        </Badge>
      );
    case "DELETED":
      return (
        <Badge variant="outline" className="bg-gray-100 text-gray-600 border-gray-200 font-medium px-2.5 py-0.5 rounded-full whitespace-nowrap">
          Đã xóa
        </Badge>
      );
    default:
      return (
        <Badge variant="outline" className="bg-gray-50 text-gray-500 border-gray-200 font-medium px-2.5 py-0.5 rounded-full whitespace-nowrap">
          {status}
        </Badge>
      );
  }
};

export default function AdminFilesPage() {
  const { user } = useAuth();
  const [currentPage, setCurrentPage] = useState(1);
  const [search, setSearch] = useState("");
  const [currentFolderId, setCurrentFolderId] = useState<string | null>(null);
  const [selectedFile, setSelectedFile] = useState<FileMetadataResponse | null>(null);

  const [filterType, setFilterType] = useState<string>("ALL");
  const [filterStatus, setFilterStatus] = useState<string>("ALL");
  const [selectedTenantId, setSelectedTenantId] = useState<string>("ALL");
  const [sortBy, setSortBy] = useState<string>("CREATED_AT");
  const [sortDirection, setSortDirection] = useState<"ASC" | "DESC">("DESC");

  // Dialog states for item actions
  const [folderToRename, setFolderToRename] = useState<{ id: string; name: string } | null>(null);
  const [folderToDelete, setFolderToDelete] = useState<{ id: string; name: string } | null>(null);
  const [itemToMove, setItemToMove] = useState<{ id: string; name: string; type: "FOLDER" | "FILE"; parentId: string | null } | null>(null);
  const [fileToDelete, setFileToDelete] = useState<any>(null); // FileMetadataResponse
  const [fileToHardDelete, setFileToHardDelete] = useState<any>(null); // FileMetadataResponse
  const [fileToRename, setFileToRename] = useState<{ id: string; name: string } | null>(null);
  const [fileToPreview, setFileToPreview] = useState<{ id: string; originalName: string; mimeType: string | null; sizeBytes: number; status: string } | null>(null);
  const [itemToShare, setItemToShare] = useState<{ id: string; name: string; type: "FOLDER" | "FILE"; mimeType?: string | null } | null>(null);

  // Context menu state
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; item: any } | null>(null);

  const handleContextMenu = useCallback((e: React.MouseEvent, item: any) => {
    if (item.type === "file" && item.status === "DELETED") {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    const menuW = 224; // ~w-56

    let menuH = 280;
    const isFolder = item.type === "folder" || !item.mimeType;
    const isPendingScanOrError =
      !isFolder &&
      (item.status === "PENDING" ||
        item.status === "SCANNING" ||
        item.status === "UPLOAD_FAILED" ||
        item.status === "INFECTED");
    const isPendingDelete = !isFolder && item.status === "PENDING_DELETE";
    const isOwnedByMe = item.actor?.id === user?.id;
    const isSystem =
      !item.actor ||
      !item.actor.id ||
      item.actor.fullName === "System" ||
      item.uploadedBy?.fullName === "System";
    const isOwnedByMeOrSystem = isOwnedByMe || isSystem;

    if (isPendingScanOrError) {
      menuH = 50;
    } else if (isPendingDelete) {
      menuH = 120;
    } else if (!isFolder && !isOwnedByMeOrSystem) {
      menuH = 50;
    } else if (isFolder && !isOwnedByMeOrSystem) {
      menuH = 50;
    }

    const x = e.clientX + menuW > window.innerWidth ? e.clientX - menuW : e.clientX;
    const y = e.clientY + menuH > window.innerHeight ? e.clientY - menuH : e.clientY;
    setContextMenu({ x, y, item });
  }, [user]);

  const closeContextMenu = useCallback(() => setContextMenu(null), []);

  useEffect(() => {
    if (!contextMenu) return;
    const handleKey = (e: KeyboardEvent) => { if (e.key === "Escape") closeContextMenu(); };
    window.addEventListener("keydown", handleKey);
    window.addEventListener("click", closeContextMenu);
    return () => {
      window.removeEventListener("keydown", handleKey);
      window.removeEventListener("click", closeContextMenu);
    };
  }, [contextMenu, closeContextMenu]);

  const handleDownload = useCallback(async (file: FileMetadataResponse) => {
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
    }
  }, []);

  const handlePreview = useCallback(async (file: FileMetadataResponse) => {
    try {
      const { getUrl } = await getPresignedGetUrl(file.id);
      window.open(getUrl, "_blank", "noopener,noreferrer");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to preview file");
    }
  }, []);

  const handleAction = async (e: React.MouseEvent, action: string, item: any) => {
    e.stopPropagation();
    const itemName = item.name || item.originalName;
    const isFolder = item.type === "folder" || !item.mimeType;

    switch (action) {
      case "download":
        if (isFolder) toast("Tính năng tải xuống thư mục sắp ra mắt");
        else handleDownload({ id: item.id, originalName: itemName } as FileMetadataResponse);
        break;
      case "preview":
        if (!isFolder) handlePreview({ id: item.id, originalName: itemName } as FileMetadataResponse);
        break;
      case "rename":
        if (isFolder) setFolderToRename({ id: item.id, name: itemName });
        else setFileToRename({ id: item.id, name: itemName });
        break;
      case "share":
        setItemToShare({
          id: item.id,
          name: itemName,
          type: isFolder ? "FOLDER" : "FILE",
          mimeType: item.mimeType || null
        });
        break;
      case "info":
        handleItemSelect(item);
        break;
      case "move":
        setItemToMove({
          id: item.id,
          name: itemName,
          type: isFolder ? "FOLDER" : "FILE",
          parentId: item.parentId ?? item.folderId ?? null
        });
        break;
      case "delete": // soft delete (remove) for ACTIVE files
        if (isFolder) setFolderToDelete({ id: item.id, name: itemName });
        else setFileToDelete(item);
        break;
      case "delete-permanent": // hard delete for PENDING_DELETE or ACTIVE files
        if (isFolder) setFolderToDelete({ id: item.id, name: itemName });
        else setFileToHardDelete(item);
        break;
      case "restore":
        try {
          await restoreFile(item.id);
          toast.success(`Đã khôi phục "${itemName}" thành công`);
          mutate();
        } catch (err) {
          toast.error(err instanceof Error ? err.message : "Khôi phục thất bại");
        }
        break;
      case "copy-link":
        try {
          const { getUrl } = await getPresignedGetUrl(item.id);
          await navigator.clipboard.writeText(getUrl);
          toast.success("Sao chép liên kết thành công!");
        } catch (err) {
          toast.error(err instanceof Error ? err.message : "Sao chép liên kết thất bại");
        }
        break;
    }
  };

  const handleSuccess = () => {
    mutate();
    setFileToDelete(null);
    setFileToHardDelete(null);
    setFolderToDelete(null);
    setFolderToRename(null);
    setFileToRename(null);
    setItemToMove(null);
    setSelectedFile(null);
  };

  const debouncedSearch = useDebounce(search, 400);

  const handleSort = (column: string) => {
    if (sortBy === column) {
      setSortDirection(sortDirection === "ASC" ? "DESC" : "ASC");
    } else {
      setSortBy(column);
      setSortDirection("ASC");
    }
  };


  // SWR for breadcrumbs (only if we are inside a folder)
  const { data: breadcrumbs } = useSWR(
    currentFolderId ? ["admin-breadcrumbs", currentFolderId] : null,
    ([, id]) => getFolderBreadcrumbs(id),
    { revalidateOnFocus: false }
  );

  // SWR for fetching all tenants to show in the filter
  const { data: tenantsData } = useSWR(
    "admin-tenants-list",
    () => listTenants({ page: 1, limit: 30 }),
    { revalidateOnFocus: false }
  );
  const tenantsList = tenantsData?.data ?? [];

  // SWR for fetching admin items
  const swrKey = ["admin-items", currentPage, debouncedSearch, currentFolderId, filterType, filterStatus, sortBy, sortDirection, selectedTenantId] as const;
  const { data, error, isLoading, mutate } = useSWR(
    swrKey,
    async ([, page, q, fId, fType, fStatus, sBy, sDir, tId]) => {
      return getAdminFolderItems({
        page,
        limit: DEFAULT_PAGE_SIZE,
        search: q || undefined,
        parentId: fId || undefined,
        type: fType !== "ALL" ? (fType as any) : undefined,
        status: fStatus !== "ALL" ? fStatus : undefined,
        tenantId: tId !== "ALL" ? tId : undefined,
        sortBy: sBy as any,
        sortDirection: sDir as any,
      });
    },
    { revalidateOnFocus: false }
  );

  const items = data?.data ?? [];
  const totalPages = data?.totalPages ?? 1;

  const handleDirectDownload = async (fileId: string, originalName: string) => {
    try {
      const { getUrl } = await getPresignedGetUrl(fileId);
      const link = document.createElement("a");
      link.href = getUrl;
      link.setAttribute("download", originalName);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch (err) {
      console.error("Direct download failed", err);
    }
  };

  function handleItemSelect(item: any) {
    if (item.type === "folder") {
      setCurrentFolderId(item.id);
      setCurrentPage(1);
      setSelectedFile(null);
    } else {
      if (item.status === "ACTIVE") {
        setSelectedFile(null);
        const fileName = item.name ?? (item as any).originalName ?? item.originalName;
        if (isFilePreviewable(fileName, item.mimeType)) {
          setFileToPreview({
            id: item.id,
            originalName: fileName,
            mimeType: item.mimeType ?? "application/octet-stream",
            sizeBytes: item.sizeBytes ?? 0,
            status: item.status ?? "ACTIVE",
          });
        } else {
          handleDirectDownload(item.id, fileName);
        }
      } else {
        const fileData = {
          id: item.id,
          originalName: item.name ?? (item as any).originalName,
          mimeType: item.mimeType ?? "application/octet-stream",
          sizeBytes: item.sizeBytes ?? 0,
          status: item.status ?? "ACTIVE",
          folderId: item.parentId ?? null,
          tenantId: item.tenantId ?? null,
          checksumMd5: null,
          thumbnailKey: null,
          isIndexed: false,
          uploadedBy: item.actor ?? { id: "", fullName: "—" },
          deletedAt: null,
          scheduledDeleteAt: null,
          createdAt: item.createdAt,
          updatedAt: item.updatedAt ?? item.createdAt,
        };

        if (selectedFile?.id === item.id) {
          setSelectedFile(null);
        } else {
          setSelectedFile(fileData);
        }
      }
    }
  }

  return (
    <div className="flex h-full flex-col overflow-hidden bg-gray-50">
      {/* ── Top search bar ── */}
      <div className="flex flex-col gap-4 border-b border-gray-200 bg-white px-6 py-4 shrink-0 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-2">
          <div className="flex items-center justify-center w-8 h-8 rounded-lg bg-red-100 text-red-600">
            <ShieldAlert className="w-4 h-4" />
          </div>
          <div>
            <h1 className="text-sm font-semibold text-gray-900">Quản lý tệp tin</h1>
            <p className="text-xs text-gray-500">Giao diện quản lý toàn cục hệ thống</p>
          </div>
        </div>
        <div className="flex flex-col sm:flex-row items-center gap-3 w-full sm:w-auto">
          <Select value={filterType} onValueChange={(v) => { 
            setFilterType(v); 
            setCurrentPage(1); 
            if (v === "FOLDER") {
              setFilterStatus("ALL");
            }
          }}>
            <SelectTrigger className="w-full sm:w-[130px] h-9 text-sm">
              <SelectValue placeholder="Loại" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">Tất cả loại</SelectItem>
              <SelectItem value="FOLDER">Thư mục</SelectItem>
              <SelectItem value="FILE">Tệp tin</SelectItem>
            </SelectContent>
          </Select>

          <Select disabled={filterType === "FOLDER"} value={filterStatus} onValueChange={(v) => { setFilterStatus(v); setCurrentPage(1); }}>
            <SelectTrigger className="w-full sm:w-[150px] h-9 text-sm">
              <SelectValue placeholder="Trạng thái" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">Tất cả trạng thái</SelectItem>
              <SelectItem value="ACTIVE">Hoạt động</SelectItem>
              <SelectItem value="PENDING">Đang chờ</SelectItem>
              <SelectItem value="SCANNING">Đang quét</SelectItem>
              <SelectItem value="INFECTED">Nhiễm virus</SelectItem>
              <SelectItem value="PENDING_DELETE">Chờ xóa</SelectItem>
              <SelectItem value="DELETED">Đã xóa</SelectItem>
              <SelectItem value="UPLOAD_FAILED">Tải lên thất bại</SelectItem>
            </SelectContent>
          </Select>

          <Select value={selectedTenantId} onValueChange={(v) => { setSelectedTenantId(v); setCurrentPage(1); }}>
            <SelectTrigger className="w-full sm:w-[180px] h-9 text-sm">
              <SelectValue placeholder="Tổ chức" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">Tất cả tổ chức</SelectItem>
              {tenantsList.map((t) => (
                <SelectItem key={t.id} value={t.id}>
                  {t.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <div className="relative w-full sm:w-[250px]">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
            <Input
              className="h-9 border-gray-200 bg-gray-50 pl-9 text-sm placeholder:text-gray-400 focus-visible:ring-primary/30 w-full"
              placeholder="Tìm kiếm tên tệp, thư mục..."
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setCurrentPage(1);
              }}
            />
          </div>
        </div>
      </div>

      {/* ── Main content area ── */}
      <div className="flex flex-1 overflow-hidden min-h-0">
        {/* ─────────── File list column ─────────── */}
        <div className="flex flex-col overflow-hidden transition-all duration-300 min-w-0 flex-1">
          {/* Breadcrumb */}
          <div className="flex items-center border-b border-gray-200 bg-white px-6 py-3 shrink-0 overflow-x-auto whitespace-nowrap hide-scrollbar">
            <div className="flex items-center gap-1.5 text-sm">
              <span
                className="flex items-center gap-1 text-gray-500 hover:text-gray-700 cursor-pointer transition-colors"
                onClick={() => {
                  setCurrentFolderId(null);
                  setCurrentPage(1);
                }}
              >
                <Home className="h-3.5 w-3.5" />
                Thư mục gốc
              </span>

              {breadcrumbs?.map((crumb) => {
                if (crumb.name === "root") return null;
                const isLast = crumb.id === currentFolderId;
                return (
                  <div key={crumb.id} className="flex items-center gap-1.5">
                    <ChevronRight className="h-3.5 w-3.5 text-gray-400 shrink-0" />
                    <span
                      className={cn(
                        "cursor-pointer transition-colors",
                        isLast ? "font-semibold text-gray-800 cursor-default" : "text-gray-500 hover:text-gray-700"
                      )}
                      onClick={() => {
                        if (!isLast) {
                           setCurrentFolderId(crumb.id);
                           setCurrentPage(1);
                        }
                      }}
                    >
                      {crumb.name}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Scrollable content */}
          <div className="flex-1 overflow-y-auto px-6 py-4 space-y-4 min-h-0">
            {error && !isLoading && (
              <div className="flex items-center gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span className="flex-1">
                  {error instanceof Error ? error.message : "Không thể tải danh sách tệp tin"}
                </span>
                <Button
                  size="sm"
                  variant="outline"
                  className="gap-1.5 text-red-600 hover:text-red-700 border-red-200"
                  onClick={() => mutate()}
                >
                  <RefreshCw className="h-3.5 w-3.5" />
                  Thử lại
                </Button>
              </div>
            )}

            <AdminFileTable
              items={items}
              isLoading={isLoading}
              sortBy={sortBy}
              sortDirection={sortDirection}
              selectedFileId={selectedFile?.id ?? null}
              user={user}
              handleSort={handleSort}
              handleItemSelect={handleItemSelect}
              handleContextMenu={handleContextMenu}
              handleAction={handleAction}
            />

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="pb-2">
                <Pagination>
                  <PaginationContent>
                    <PaginationItem>
                      <PaginationPrevious
                        href="#"
                        onClick={(e) => { e.preventDefault(); setCurrentPage((p) => Math.max(1, p - 1)); }}
                        className={currentPage === 1 ? "pointer-events-none opacity-50" : ""}
                      />
                    </PaginationItem>
                    {Array.from({ length: totalPages }, (_, i) => i + 1).map((page) => (
                      <PaginationItem key={page}>
                        <PaginationLink
                          href="#"
                          isActive={page === currentPage}
                          onClick={(e) => { e.preventDefault(); setCurrentPage(page); }}
                        >
                          {page}
                        </PaginationLink>
                      </PaginationItem>
                    ))}
                    <PaginationItem>
                      <PaginationNext
                        href="#"
                        onClick={(e) => { e.preventDefault(); setCurrentPage((p) => Math.min(totalPages, p + 1)); }}
                        className={currentPage === totalPages ? "pointer-events-none opacity-50" : ""}
                      />
                    </PaginationItem>
                  </PaginationContent>
                </Pagination>
              </div>
            )}
          </div>
        </div>

        {/* ─────────── File Detail Panel (slides in when file selected) ─────────── */}
        <div className={cn(
          "flex flex-col border-l border-gray-200 bg-white overflow-hidden transition-all duration-300",
          selectedFile
            ? "w-72 xl:w-80 opacity-100"
            : "w-0 opacity-0 border-l-0"
        )}>
          {selectedFile && (
            <>
              {/* Close button */}
              <div className="relative">
                <button
                  onClick={() => setSelectedFile(null)}
                  className="absolute right-4 top-4 z-10 flex h-6 w-6 items-center justify-center rounded-full text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors"
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              </div>
              <FileDetailCard
                file={selectedFile}
                mode={selectedFile.status === "PENDING_DELETE" ? "trash" : "default"}
                onMutate={() => {
                  mutate();
                  setSelectedFile(null);
                }}
                onDelete={(file) => {
                  if (file.status === "PENDING_DELETE") {
                    setFileToHardDelete(file);
                  } else {
                    setFileToDelete(file);
                  }
                }}
                onMove={(file) => {
                  setItemToMove({
                    id: file.id,
                    name: file.originalName,
                    type: "FILE",
                    parentId: file.folderId
                  });
                }}
              />
            </>
          )}
        </div>
      </div>

      {/* ── Dialogs ── */}
      {folderToRename && (
        <RenameFolderDialog
          open={!!folderToRename}
          onOpenChange={(open) => !open && setFolderToRename(null)}
          folderId={folderToRename.id}
          currentName={folderToRename.name}
          onSuccess={handleSuccess}
        />
      )}
      {fileToRename && (
        <RenameFileDialog
          open={!!fileToRename}
          onOpenChange={(open) => !open && setFileToRename(null)}
          fileId={fileToRename.id}
          currentName={fileToRename.name}
          onSuccess={handleSuccess}
        />
      )}
      {folderToDelete && (
        <DeleteFolderDialog
          open={!!folderToDelete}
          onOpenChange={(open) => !open && setFolderToDelete(null)}
          folderId={folderToDelete.id}
          folderName={folderToDelete.name}
          onSuccess={handleSuccess}
        />
      )}
      <MoveItemDialog
        open={!!itemToMove}
        onOpenChange={(open) => !open && setItemToMove(null)}
        item={itemToMove}
        onSuccess={handleSuccess}
      />
      <DeleteFileDialog
        file={fileToDelete}
        open={!!fileToDelete}
        onOpenChange={(open) => !open && setFileToDelete(null)}
        onSuccess={handleSuccess}
        isDirectDelete={false}
      />
      <DeleteFileDialog
        file={fileToHardDelete}
        open={!!fileToHardDelete}
        onOpenChange={(open) => !open && setFileToHardDelete(null)}
        onSuccess={handleSuccess}
        isDirectDelete={true}
      />

      <FilePreviewDialog
        open={!!fileToPreview}
        onOpenChange={(open) => !open && setFileToPreview(null)}
        file={fileToPreview}
      />
      <ShareItemDialog
        open={!!itemToShare}
        onOpenChange={(open) => !open && setItemToShare(null)}
        item={itemToShare}
      />

      {/* ── Right-click context menu overlay ── */}
      {contextMenu && (
        <div
          className="fixed inset-0 z-50"
          onClick={closeContextMenu}
          onContextMenu={(e) => e.preventDefault()}
        >
          <div
            className="absolute w-56 rounded-lg border border-gray-200 bg-white py-1.5 shadow-xl"
            style={{ left: contextMenu.x, top: contextMenu.y }}
            onClick={(e) => e.stopPropagation()}
          >
            {(() => {
              const isOwnedByMe = contextMenu.item.actor?.id === user?.id;
              const isSystem =
                !contextMenu.item.actor ||
                !contextMenu.item.actor.id ||
                contextMenu.item.actor.fullName === "System" ||
                contextMenu.item.uploadedBy?.fullName === "System";
              const isOwnedByMeOrSystem = isOwnedByMe || isSystem;
              const isFolder = contextMenu.item.type === "folder" || !contextMenu.item.mimeType;

              if (isFolder) {
                if (isOwnedByMeOrSystem) {
                  return (
                    <>
                      <button
                        className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-gray-700 hover:bg-gray-50 cursor-pointer"
                        onClick={(e) => { closeContextMenu(); handleAction(e, "rename", contextMenu.item); }}
                      >
                        <Pencil className="h-4 w-4 text-gray-500 mr-2" />
                        Đổi tên
                      </button>
                      <button
                        className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-gray-700 hover:bg-gray-50 cursor-pointer"
                        onClick={(e) => { closeContextMenu(); handleAction(e, "move", contextMenu.item); }}
                      >
                        <MoveRight className="h-4 w-4 text-gray-500 mr-2" />
                        Di chuyển
                      </button>
                      <button
                        className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-gray-700 hover:bg-gray-50 cursor-pointer"
                        onClick={(e) => { closeContextMenu(); handleItemSelect(contextMenu.item); }}
                      >
                        <Info className="h-4 w-4 text-gray-500 mr-2" />
                        Thông tin về thư mục
                      </button>
                      <div className="my-1 border-t border-gray-100" />
                      <button
                        className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-red-600 hover:bg-red-50 cursor-pointer font-medium"
                        onClick={(e) => { closeContextMenu(); handleAction(e, "delete", contextMenu.item); }}
                      >
                        <Trash2 className="h-4 w-4 text-red-500 mr-2" />
                        Xóa vĩnh viễn
                      </button>
                    </>
                  );
                } else {
                  return (
                    <button
                      className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-red-600 hover:bg-red-50 cursor-pointer font-medium"
                      onClick={(e) => { closeContextMenu(); handleAction(e, "delete", contextMenu.item); }}
                    >
                      <Trash2 className="h-4 w-4 text-red-500 mr-2" />
                      Xóa vĩnh viễn
                    </button>
                  );
                }
              } else {
                // It is a File
                const isPendingDelete = contextMenu.item.status === "PENDING_DELETE";
                const isPendingScanOrError =
                  contextMenu.item.status === "PENDING" ||
                  contextMenu.item.status === "SCANNING" ||
                  contextMenu.item.status === "UPLOAD_FAILED" ||
                  contextMenu.item.status === "INFECTED";

                if (isPendingScanOrError) {
                  return (
                    <button
                      className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-red-600 hover:bg-red-50 cursor-pointer font-medium"
                      onClick={(e) => { closeContextMenu(); handleAction(e, "delete-permanent", contextMenu.item); }}
                    >
                      <Trash2 className="h-4 w-4 text-red-500 mr-2" />
                      Xóa vĩnh viễn
                    </button>
                  );
                }

                if (isPendingDelete) {
                  return (
                    <>
                      <button
                        className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-gray-700 hover:bg-gray-50 cursor-pointer"
                        onClick={(e) => { closeContextMenu(); handleAction(e, "restore", contextMenu.item); }}
                      >
                        <RotateCcw className="h-4 w-4 text-gray-500 mr-2" />
                        Khôi phục
                      </button>
                      <div className="my-1 border-t border-gray-100" />
                      <button
                        className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-red-600 hover:bg-red-50 cursor-pointer font-medium"
                        onClick={(e) => { closeContextMenu(); handleAction(e, "delete-permanent", contextMenu.item); }}
                      >
                        <Trash2 className="h-4 w-4 text-red-500 mr-2" />
                        Xóa vĩnh viễn
                      </button>
                    </>
                  );
                } else {
                  // Active/Scanning/Infected states
                  if (isOwnedByMeOrSystem) {
                    return (
                      <>
                        <button
                          className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-gray-700 hover:bg-gray-50 cursor-pointer"
                          onClick={(e) => { closeContextMenu(); handleAction(e, "download", contextMenu.item); }}
                        >
                          <Download className="h-4 w-4 text-gray-500" />
                          Tải xuống
                        </button>
                        <button
                          className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-gray-700 hover:bg-gray-50 cursor-pointer"
                          onClick={(e) => { closeContextMenu(); handleAction(e, "rename", contextMenu.item); }}
                        >
                          <Pencil className="h-4 w-4 text-gray-500" />
                          Đổi tên
                        </button>
                        <div className="my-1 border-t border-gray-100" />
                        {!isFolder && contextMenu.item.status === "ACTIVE" && (
                          <button
                            className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-gray-700 hover:bg-gray-50 cursor-pointer"
                            onClick={(e) => { closeContextMenu(); handleAction(e, "copy-link", contextMenu.item); }}
                          >
                            <Link className="h-4 w-4 text-gray-500" />
                            Sao chép liên kết
                          </button>
                        )}
                        <button
                          className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-gray-700 hover:bg-gray-50 cursor-pointer"
                          onClick={(e) => { closeContextMenu(); handleAction(e, "share", contextMenu.item); }}
                        >
                          <Share2 className="h-4 w-4 text-gray-500" />
                          Chia sẻ
                        </button>
                        <button
                          className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-gray-700 hover:bg-gray-50 cursor-pointer"
                          onClick={(e) => { closeContextMenu(); handleAction(e, "move", contextMenu.item); }}
                        >
                          <MoveRight className="h-4 w-4 text-gray-500" />
                          Di chuyển
                        </button>
                        <button
                          className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-gray-700 hover:bg-gray-50 cursor-pointer"
                          onClick={(e) => { closeContextMenu(); handleItemSelect(contextMenu.item); }}
                        >
                          <Info className="h-4 w-4 text-gray-500" />
                          Thông tin về tệp
                        </button>
                        <div className="my-1 border-t border-gray-100" />
                        <button
                          className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-gray-700 hover:bg-gray-50 cursor-pointer"
                          onClick={(e) => { closeContextMenu(); handleAction(e, "delete", contextMenu.item); }}
                        >
                          <Trash2 className="h-4 w-4 text-gray-500" />
                          Chuyển vào thùng rác
                        </button>
                      </>
                    );
                  } else {
                    return (
                      <button
                        className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-red-600 hover:bg-red-50 cursor-pointer font-medium"
                        onClick={(e) => { closeContextMenu(); handleAction(e, "delete-permanent", contextMenu.item); }}
                      >
                        <Trash2 className="h-4 w-4 text-red-500 mr-2" />
                        Xóa vĩnh viễn
                      </button>
                    );
                  }
                }
              }
            })()}
          </div>
        </div>
      )}
    </div>
  );
}
