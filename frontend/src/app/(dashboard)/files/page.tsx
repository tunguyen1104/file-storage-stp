"use client";

import React, { useState, useCallback, useRef, Suspense, useEffect, memo } from "react";
import { useSearchParams } from "next/navigation";
import useSWR from "swr";
import {
  AlertCircle,
  RefreshCw,
  Search,
  Plus,
  LayoutGrid,
  List,
  CloudUpload,
  FolderOpen,
  FileImage,
  FileText,
  FileArchive,
  FileVideo,
  FileAudio,
  File as FileIcon,
  ChevronRight,
  Home,
  Folder as FolderIcon,
  ArrowUp,
  ArrowDown,
  UserCircle2,
  Download,
  Pencil,
  Share2,
  MoveRight,
  Info,
  Trash2,
  RotateCcw,
  Link,
} from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";
import { FileActionMenu } from "@/components/files/FileActionMenu";
import { DeleteFileDialog } from "@/components/files/DeleteFileDialog";
import { RenameFileDialog } from "@/components/files/RenameFileDialog";
import { NewFileMenu } from "@/components/files/NewFileMenu";
import { useUpload } from "@/contexts/upload-context";
import { listFiles, downloadFile, getPresignedGetUrl, restoreFile, deleteFileDirect } from "@/lib/api/file";
import { listFolderItems, getFolderBreadcrumbs } from "@/lib/api/folder";
import { useDebounce } from "@/hooks/use-debounce";
import { CreateFolderDialog } from "@/components/folders/CreateFolderDialog";
import { RenameFolderDialog } from "@/components/folders/RenameFolderDialog";
import { DeleteFolderDialog } from "@/components/folders/DeleteFolderDialog";
import { MoveItemDialog } from "@/components/files/MoveItemDialog";
import { ItemInfoDialog } from "@/components/files/ItemInfoDialog";
import { FilePreviewDialog } from "@/components/files/FilePreviewDialog";
import { ShareItemDialog } from "@/components/files/ShareItemDialog";
import {
  DEFAULT_PAGE_SIZE,
  FILE_STATUS,
  FILE_SORT_BY,
  SORT_DIRECTION,
  MAX_FILE_SIZE,
} from "@/lib/constants";
import type { FileSortBy, SortDirection } from "@/lib/constants";
import type { FileMetadataResponse } from "@/types/file/file";

import { cn } from "@/lib/utils";

// ─── File icon helpers ───────────────────────────────────────────

function renderFileIcon(mimeType: string | null, size = "h-5 w-5") {
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

function formatBytes(bytes: number): string {
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

function getTypeName(mimeType: string | null): string {
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

// ─── Inner page (uses useSearchParams inside Suspense) ───────────

function FilesPageInner() {
  const searchParams = useSearchParams();
  const tab = searchParams.get("tab"); // null | "trash" | "shared"
  const isTrash = tab === "trash";

  // Determine status filter based on tab
  const statusFilter = isTrash ? FILE_STATUS.PENDING_DELETE : FILE_STATUS.ACTIVE;
  const pageLabel = isTrash ? "Thùng rác" : "Tất cả tệp tin";
  const emptyLabel = isTrash
    ? "Thùng rác trống."
    : "Chưa có tệp tin nào. Hãy tải lên tệp tin mới để bắt đầu.";

  // ── State ──
  const [currentPage, setCurrentPage] = useState(1);
  const [search, setSearch] = useState("");
  const [viewMode, setViewMode] = useState<"list" | "grid">("list");
  const [sort, setSort] = useState<{ sortBy: FileSortBy; sortDirection: SortDirection }>({
    sortBy: FILE_SORT_BY.CREATED_AT,
    sortDirection: SORT_DIRECTION.DESC,
  });
  const debouncedSearch = useDebounce(search, 400);

  const { addFiles } = useUpload();
  const [newFileMenuOpen, setNewFileMenuOpen] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [currentFolderId, setCurrentFolderId] = useState<string | null>(null);
  const [createFolderOpen, setCreateFolderOpen] = useState(false);
  
  // Dialog states for row actions
  const [folderToRename, setFolderToRename] = useState<{ id: string; name: string } | null>(null);
  const [folderToDelete, setFolderToDelete] = useState<{ id: string; name: string } | null>(null);
  const [itemToMove, setItemToMove] = useState<{ id: string; name: string; type: "FOLDER" | "FILE"; parentId: string | null } | null>(null);
  const [fileToDelete, setFileToDelete] = useState<any>(null); // FileMetadataResponse
  const [fileToRename, setFileToRename] = useState<{ id: string; name: string } | null>(null);
  const [itemToShowInfo, setItemToShowInfo] = useState<{ id: string; type: "folder" | "file" } | null>(null);
  const [fileToPreview, setFileToPreview] = useState<{ id: string; originalName: string; mimeType: string | null; sizeBytes: number; status: string } | null>(null);
  const [itemToShare, setItemToShare] = useState<{ id: string; name: string; type: "FOLDER" | "FILE"; mimeType?: string | null } | null>(null);

  // ── Context menu state ──
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; item: any } | null>(null);

  const handleContextMenu = useCallback((e: React.MouseEvent, item: any) => {
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

    if (isPendingScanOrError) {
      menuH = 50;
    } else if (isTrash) {
      menuH = 120;
    }

    const x = e.clientX + menuW > window.innerWidth ? e.clientX - menuW : e.clientX;
    const y = e.clientY + menuH > window.innerHeight ? e.clientY - menuH : e.clientY;
    setContextMenu({ x, y, item });
  }, [isTrash]);

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

  useEffect(() => {
    setCurrentPage(1);
    setSearch("");
    setCurrentFolderId(null);
  }, [tab]);

  // Initialize folderId from URL param (used when navigating from Shared page)
  const urlFolderId = searchParams.get("folderId");
  const sharedPermission = searchParams.get("sharedPermission"); // "VIEW" | "EDIT" | null
  const isViewOnly = sharedPermission === "VIEW";

  useEffect(() => {
    if (urlFolderId) {
      setCurrentFolderId(urlFolderId);
      setCurrentPage(1);
    }
  }, [urlFolderId]);

  const newFileButtonRef = useRef<HTMLButtonElement>(null);

  // ── Data ──
  const swrKey = ["items", currentPage, debouncedSearch, statusFilter, sort.sortBy, sort.sortDirection, tab, currentFolderId] as const;
  const { data, error, isLoading, mutate } = useSWR(
    swrKey,
    async ([, page, q, status, sortBy, sortDirection, t, fId]) => {
      if (t === "trash") {
        return listFiles({
          page,
          limit: DEFAULT_PAGE_SIZE,
          search: q || undefined,
          status,
          sortBy,
          sortDirection,
        });
      } else {
        return listFolderItems(fId, {
          page,
          size: DEFAULT_PAGE_SIZE,
          search: q || undefined,
          sort: sortBy === FILE_SORT_BY.CREATED_AT ? "CREATED_AT" : sortBy === FILE_SORT_BY.SIZE_BYTES ? "SIZE_BYTES" : "NAME",
          sortOrder: sortDirection === SORT_DIRECTION.ASC ? "ASC" : "DESC",
        });
      }
    },
    { revalidateOnFocus: false }
  );

  const { data: breadcrumbs, mutate: mutateBreadcrumbs } = useSWR(
    !isTrash && currentFolderId ? ["breadcrumbs", currentFolderId] : null,
    ([, id]) => getFolderBreadcrumbs(id),
    { revalidateOnFocus: false }
  );

  // Both APIs return PageResponse<T> wrapped in ApiResponse
  const items = data?.data ?? [];
  const totalPages = data?.totalPages ?? 1;
  // Current folder name derived from breadcrumbs (used by RenameFolderDialog)
  const currentFolderName = breadcrumbs?.find((c) => c.id === currentFolderId)?.name ?? "";
  
  const currentFolderParentId = breadcrumbs && breadcrumbs.length >= 2 
    ? breadcrumbs[breadcrumbs.length - 2].id === "root" ? null : breadcrumbs[breadcrumbs.length - 2].id
    : null;

  function handleSuccess() {
    mutate();
    mutateBreadcrumbs();
    setFileToDelete(null);
    setFolderToDelete(null);
    setFolderToRename(null);
    setItemToMove(null);
  }

  const handleDirectDownload = useCallback(async (fileId: string, originalName: string) => {
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
  }, []);

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
      toast.error(err instanceof Error ? err.message : "Tải xuống tệp tin thất bại");
    }
  }, []);

  const handlePreview = useCallback(async (file: FileMetadataResponse) => {
    try {
      const { getUrl } = await getPresignedGetUrl(file.id);

      window.open(getUrl, "_blank", "noopener,noreferrer");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Không thể xem trước tệp tin");
    }
  }, []);



  const handleItemSelect = useCallback((item: any) => {
    if (item.type === "folder") {
      setCurrentFolderId(item.id);
      setCurrentPage(1);
    } else {
      if (item.status === "ACTIVE") {
        const fileName = item.name ?? item.originalName;
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
        // Just preview the file
        handlePreview({
          id: item.id,
          originalName: item.name ?? item.originalName,
          mimeType: item.mimeType ?? "application/octet-stream",
          sizeBytes: item.sizeBytes ?? 0,
          status: item.status ?? "ACTIVE",
          folderId: item.parentId ?? null,
          tenantId: null,
          checksumMd5: null,
          thumbnailKey: null,
          isIndexed: false,
          uploadedBy: item.actor ?? { id: "", fullName: "—" },
          deletedAt: null,
          scheduledDeleteAt: null,
          createdAt: item.createdAt,
          updatedAt: item.updatedAt ?? item.createdAt,
        });
      }
    }
  }, [handleDirectDownload, handlePreview]);

  const handleSort = useCallback((newSortBy: FileSortBy) => {
    setSort(prev => {
      if (prev.sortBy === newSortBy) {
        return { ...prev, sortDirection: prev.sortDirection === SORT_DIRECTION.ASC ? SORT_DIRECTION.DESC : SORT_DIRECTION.ASC };
      }
      return { sortBy: newSortBy, sortDirection: SORT_DIRECTION.DESC };
    });
    setCurrentPage(1);
  }, []);

  const handleAction = useCallback(async (e: React.MouseEvent, action: string, item: any) => {
    e.stopPropagation();
    const itemName = item.name || item.originalName;
    const isFolder = item.type === "folder" || !item.mimeType;

    switch (action) {
      case "download":
        if (isFolder) toast("Tính năng tải xuống thư mục sắp ra mắt");
        else handleDownload({ id: item.id, originalName: itemName } as FileMetadataResponse);
        break;
      case "rename":
        if (isFolder) setFolderToRename({ id: item.id, name: itemName });
        else setFileToRename({ id: item.id, name: itemName });
        break;
      case "copy":
        toast("Tính năng nhân bản sắp ra mắt");
        break;
      case "share":
        setItemToShare({
          id: item.id,
          name: itemName,
          type: isFolder ? "FOLDER" : "FILE",
          mimeType: item.mimeType || null
        });
        break;
      case "move":
        setItemToMove({
          id: item.id,
          name: itemName,
          type: isFolder ? "FOLDER" : "FILE",
          parentId: item.parentId ?? item.folderId ?? null
        });
        break;
      case "info":
        setItemToShowInfo({
          id: item.id,
          type: isFolder ? "folder" : "file",
        });
        break;
      case "delete":
        if (isFolder) setFolderToDelete({ id: item.id, name: itemName });
        else setFileToDelete(item);
        break;
      case "restore":
        try {
          await restoreFile(item.id);
          toast.success(`Đã khôi phục "${itemName}"`);
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
      case "delete-permanent":
        try {
          await deleteFileDirect(item.id);
          toast.success(`Đã xóa vĩnh viễn "${itemName}"`);
          mutate();
        } catch (err) {
          toast.error(err instanceof Error ? err.message : "Xóa thất bại");
        }
        break;
    }
  }, [handleDownload, mutate]);

  // ── Drag & drop ──
  const [isDragging, setIsDragging] = useState(false);

  return (
    <div className="flex h-full flex-col overflow-hidden bg-gray-50">

      {/* ── Main content area ── */}
      <div className="flex flex-1 overflow-hidden min-h-0">
        {/* ─────────── File list column ─────────── */}
        <div className="flex flex-col flex-1 overflow-hidden transition-all duration-300 min-w-0">
          {/* Breadcrumb + toolbar */}
          <div className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-3 shrink-0">
            <div className="flex items-center gap-1.5 text-sm overflow-x-auto whitespace-nowrap hide-scrollbar">
              <span
                className="flex items-center gap-1 text-gray-500 hover:text-gray-700 cursor-pointer transition-colors"
                onClick={() => {
                  setCurrentFolderId(null);
                  setCurrentPage(1);
                }}
              >
                <Home className="h-3.5 w-3.5" />
                Tệp tin của tôi
              </span>

              {!isTrash && (
                <>
                  <ChevronRight className="h-3.5 w-3.5 text-gray-400 shrink-0" />
                  <span
                    className={cn(
                      "cursor-pointer transition-colors",
                      !currentFolderId ? "font-semibold text-gray-800 cursor-default" : "text-gray-500 hover:text-gray-700"
                    )}
                    onClick={() => {
                      if (currentFolderId) {
                        setCurrentFolderId(null);
                        setCurrentPage(1);
                      }
                    }}
                  >
                    {isViewOnly ? "Thư mục được chia sẻ" : "Tất cả tệp tin"}
                  </span>
                </>
              )}

              {!isTrash && breadcrumbs?.map((crumb) => {
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

              {isTrash && (
                <>
                  <ChevronRight className="h-3.5 w-3.5 text-gray-400 shrink-0" />
                  <span className="font-semibold text-gray-800">{pageLabel}</span>
                </>
              )}
            </div>

            <div className="flex items-center gap-2">
              {/* Grid/List toggle */}
              <div className="flex items-center rounded-lg border border-gray-200 p-0.5 bg-gray-50">
                <button
                  onClick={() => setViewMode("grid")}
                  className={cn(
                    "flex h-7 w-7 items-center justify-center rounded-md transition-colors",
                    viewMode === "grid"
                      ? "bg-white shadow-sm text-gray-800"
                      : "text-gray-400 hover:text-gray-600"
                  )}
                >
                  <LayoutGrid className="h-3.5 w-3.5" />
                </button>
                <button
                  onClick={() => setViewMode("list")}
                  className={cn(
                    "flex h-7 w-7 items-center justify-center rounded-md transition-colors",
                    viewMode === "list"
                      ? "bg-white shadow-sm text-gray-800"
                      : "text-gray-400 hover:text-gray-600"
                  )}
                >
                  <List className="h-3.5 w-3.5" />
                </button>
              </div>

              {/* Choose button (only on All Files, not Trash, not view-only shared) */}
              {!isTrash && !isViewOnly && (
                <div className="relative">
                  <Button
                    ref={newFileButtonRef}
                    size="sm"
                    className="gap-2 bg-[#7C3AED] hover:bg-[#6D28D9] text-white shadow-sm"
                    onClick={() => setNewFileMenuOpen((v) => !v)}
                  >
                    <Plus className="h-4 w-4" />
                    Thêm mới
                  </Button>
                  <NewFileMenu
                    open={newFileMenuOpen}
                    onClose={() => setNewFileMenuOpen(false)}
                    anchorRef={newFileButtonRef}
                    onUpload={() => {
                      setNewFileMenuOpen(false);
                      fileInputRef.current?.click();
                    }}
                    onCreateFolder={() => {
                      setNewFileMenuOpen(false);
                      setCreateFolderOpen(true);
                    }}
                  />
                </div>
              )}
            </div>
          </div>

          {/* Scrollable content */}
          <div className="flex-1 overflow-y-auto px-6 py-4 space-y-4 min-h-0">
            {/* Error */}
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

            {/* Upload drop zone (only on All Files, not Trash, not view-only shared) */}
            {!isTrash && !isViewOnly && (
              <div
                onDragOver={(e) => { e.preventDefault(); setIsDragging(true); }}
                onDragLeave={() => setIsDragging(false)}
                onDrop={(e) => {
                  e.preventDefault();
                  setIsDragging(false);
                  const files = Array.from(e.dataTransfer.files);
                  if (files.length > 0) {
                     addFiles(files, currentFolderId ?? undefined);
                  }
                }}
                onClick={() => fileInputRef.current?.click()}
                className={cn(
                  "flex flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed py-8 cursor-pointer transition-all",
                  isDragging
                    ? "border-[#7C3AED] bg-[#F3E8FF]/40"
                    : "border-gray-200 bg-white hover:border-[#7C3AED]/50 hover:bg-gray-100"
                )}
              >
                <div className={cn(
                  "flex h-12 w-12 items-center justify-center rounded-full transition-colors",
                  isDragging ? "bg-[#7C3AED]/10" : "bg-gray-100"
                )}>
                  <CloudUpload className={cn("h-6 w-6 transition-colors", isDragging ? "text-[#7C3AED]" : "text-gray-400")} />
                </div>
                <div className="text-center">
                  <p className="text-sm font-medium text-gray-700">Click hoặc kéo thả tệp tin vào khu vực này để tải lên</p>
                  <p className="text-xs text-gray-400 mt-0.5">Hỗ trợ mọi loại tệp tin · Tối đa {(MAX_FILE_SIZE / (1024 * 1024 * 1024)).toFixed(0)} GB mỗi tệp</p>
                </div>
              </div>
            )}

            {/* Hidden file input for drop zone */}
            {!isTrash && (
              <input
                type="file"
                multiple
                className="hidden"
                ref={fileInputRef}
                onChange={(e) => {
                  const files = Array.from(e.target.files ?? []);
                  if (files.length > 0) {
                    addFiles(files, currentFolderId ?? undefined);
                  }
                  e.target.value = '';
                }}
              />
            )}

            {viewMode === "list" ? (
              <FileListView
                items={items}
                isLoading={isLoading}
                sort={sort}
                onSort={handleSort}
                onSelect={handleItemSelect}
                onAction={handleAction}
                onContextMenu={handleContextMenu}
                isTrash={isTrash}
                emptyMessage={emptyLabel}
                isViewOnly={isViewOnly}
              />
            ) : (
              <FileGridView
                items={items}
                isLoading={isLoading}
                onSelect={handleItemSelect}
                onAction={handleAction}
                onContextMenu={handleContextMenu}
                isTrash={isTrash}
                emptyMessage={emptyLabel}
              />
            )}

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
      </div>

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
              const isFolder = contextMenu.item.type === "folder" || !contextMenu.item.mimeType;
              const isPendingScanOrError =
                !isFolder &&
                (contextMenu.item.status === "PENDING" ||
                  contextMenu.item.status === "SCANNING" ||
                  contextMenu.item.status === "UPLOAD_FAILED" ||
                  contextMenu.item.status === "INFECTED");

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

              if (isTrash) {
                return (
                  <>
                    <button
                      className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-gray-700 hover:bg-gray-50 cursor-pointer"
                      onClick={(e) => { closeContextMenu(); handleAction(e, "restore", contextMenu.item); }}
                    >
                      <RotateCcw className="h-4 w-4 text-gray-500" />
                      Khôi phục
                    </button>
                    <div className="my-1 border-t border-gray-100" />
                    <button
                      className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-red-600 hover:bg-red-50 cursor-pointer"
                      onClick={(e) => { closeContextMenu(); handleAction(e, "delete-permanent", contextMenu.item); }}
                    >
                      <Trash2 className="h-4 w-4" />
                      Xóa vĩnh viễn
                    </button>
                  </>
                );
              }

              return (
                <>
                  {!isFolder && (
                    <button
                      className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-gray-700 hover:bg-gray-50 cursor-pointer"
                      onClick={(e) => { closeContextMenu(); handleAction(e, "download", contextMenu.item); }}
                    >
                      <Download className="h-4 w-4 text-gray-500" />
                      Tải xuống
                    </button>
                  )}
                  {!isViewOnly && (
                    <button
                      className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-gray-700 hover:bg-gray-50 cursor-pointer"
                      onClick={(e) => { closeContextMenu(); handleAction(e, "rename", contextMenu.item); }}
                    >
                      <Pencil className="h-4 w-4 text-gray-500" />
                      Đổi tên
                    </button>
                  )}
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
                  {!isViewOnly && (
                    <>
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
                    </>
                  )}
                  <button
                    className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-gray-700 hover:bg-gray-50 cursor-pointer"
                    onClick={(e) => { closeContextMenu(); handleAction(e, "info", contextMenu.item); }}
                  >
                    <Info className="h-4 w-4 text-gray-500" />
                    {contextMenu.item.type === "folder" || !contextMenu.item.mimeType
                      ? "Thông tin về thư mục"
                      : "Thông tin về tệp"}
                  </button>
                  {!isViewOnly && (
                    <>
                      <div className="my-1 border-t border-gray-100" />
                      <button
                        className="flex w-full items-center gap-3 px-4 py-2.5 text-[13px] text-gray-700 hover:bg-gray-50 cursor-pointer"
                        onClick={(e) => { closeContextMenu(); handleAction(e, "delete", contextMenu.item); }}
                      >
                        <Trash2 className="h-4 w-4 text-gray-500" />
                        Chuyển vào thùng rác
                      </button>
                    </>
                  )}
                </>
              );
            })()}
          </div>
        </div>
      )}

      {/* ── Dialogs ── */}
      <CreateFolderDialog
        open={createFolderOpen}
        onOpenChange={setCreateFolderOpen}
        parentId={currentFolderId}
        onSuccess={handleSuccess}
      />
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
        isDirectDelete={isTrash}
      />
      <ItemInfoDialog
        open={!!itemToShowInfo}
        onOpenChange={(open) => !open && setItemToShowInfo(null)}
        itemId={itemToShowInfo?.id ?? null}
        itemType={itemToShowInfo?.type ?? "file"}
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
    </div>
  );
}

// ─── Sortable Header helper ─────────────────────────────────────

function SortHeader({
  label,
  sortKey,
  currentSort,
  onSort,
  className,
}: {
  label: string;
  sortKey: FileSortBy;
  currentSort: { sortBy: FileSortBy; sortDirection: SortDirection };
  onSort: (key: FileSortBy) => void;
  className?: string;
}) {
  const isActive = currentSort.sortBy === sortKey;
  return (
    <th
      className={cn(
        "px-4 py-3 text-left text-xs font-semibold select-none cursor-pointer transition-colors",
        isActive ? "text-[#7C3AED]" : "text-gray-500 hover:text-gray-700",
        className
      )}
      onClick={() => onSort(sortKey)}
    >
      <div className="flex items-center gap-1">
        {label}
        {isActive && (
          currentSort.sortDirection === SORT_DIRECTION.ASC
            ? <ArrowUp className="h-3 w-3" />
            : <ArrowDown className="h-3 w-3" />
        )}
      </div>
    </th>
  );
}

// ─── List View ───────────────────────────────────────────────────

const FileListView = memo(function FileListView({
  items,
  isLoading,
  sort,
  onSort,
  onSelect,
  onAction,
  onContextMenu,
  isTrash,
  emptyMessage,
  isViewOnly = false,
}: {
  items: any[];
  isLoading: boolean;
  sort: { sortBy: FileSortBy; sortDirection: SortDirection };
  onSort: (key: FileSortBy) => void;
  onSelect: (item: any) => void;
  onAction: (e: React.MouseEvent, action: string, item: any) => void;
  onContextMenu: (e: React.MouseEvent, item: any) => void;
  isTrash: boolean;
  emptyMessage: string;
  isViewOnly?: boolean;
}) {
  if (isLoading) {
    return (
      <div className="overflow-hidden rounded-xl border border-gray-200 bg-white">
        <table className="w-full text-sm table-fixed">
          <colgroup>
            <col className="w-[45%]" />
            <col className="w-[18%]" />
            <col className="w-[20%]" />
            <col className="w-[12%]" />
            <col className="w-[5%]" />
          </colgroup>
          <thead className="border-b border-gray-100 bg-gray-50">
            <tr>
              <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500">Tên</th>
              <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 hidden md:table-cell">Chủ sở hữu</th>
              <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 hidden sm:table-cell">Đã chỉnh sửa</th>
              <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500">Kích thước</th>
              <th className="w-10" />
            </tr>
          </thead>
          <tbody>
            {Array.from({ length: 6 }).map((_, i) => (
              <tr key={i} className="border-b border-gray-50">
                <td className="px-4 py-2.5"><div className="h-4 w-40 animate-pulse rounded bg-gray-100" /></td>
                <td className="px-4 py-2.5 hidden md:table-cell"><div className="h-4 w-24 animate-pulse rounded bg-gray-100" /></td>
                <td className="px-4 py-2.5 hidden sm:table-cell"><div className="h-4 w-28 animate-pulse rounded bg-gray-100" /></td>
                <td className="px-4 py-2.5"><div className="h-4 w-14 animate-pulse rounded bg-gray-100" /></td>
                <td className="px-1 py-2.5"><div className="h-4 w-4 animate-pulse rounded bg-gray-100 mx-auto" /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-gray-200 bg-white py-16">
        <FolderOpen className="h-10 w-10 text-gray-300" />
        <p className="text-sm text-gray-400">{emptyMessage}</p>
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-xl border border-gray-200 bg-white">
      <table className="w-full text-sm table-fixed" style={{ cursor: "pointer" }}>
        <colgroup>
          <col className="w-[45%]" />
          <col className="w-[18%]" />
          <col className="w-[20%]" />
          <col className="w-[12%]" />
          <col className="w-[5%]" />
        </colgroup>
        <thead className="border-b border-gray-100 bg-gray-50" style={{ cursor: "default" }}>
          <tr>
            <SortHeader label="Tên" sortKey={FILE_SORT_BY.ORIGINAL_NAME} currentSort={sort} onSort={onSort} />
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 hidden md:table-cell">Chủ sở hữu</th>
            <SortHeader label="Đã chỉnh sửa" sortKey={FILE_SORT_BY.CREATED_AT} currentSort={sort} onSort={onSort} className="hidden sm:table-cell" />
            <SortHeader label="Kích thước" sortKey={FILE_SORT_BY.SIZE_BYTES} currentSort={sort} onSort={onSort} />
            <th className="w-10" />
          </tr>
        </thead>
        <tbody>
          {items.map((item) => {
            const name = item.name || item.originalName;
            const date = item.updatedAt || item.createdAt;
            const owner = item.actor?.fullName || item.uploadedBy?.fullName || "—";
            return (
              <tr
                key={item.id}
                onClick={() => onSelect(item)}
                onContextMenu={(e) => onContextMenu(e, item)}
                className="group border-b border-gray-50 transition-colors hover:bg-gray-100"
                style={{ cursor: "pointer" }}
              >
                <td className="px-4 py-2.5">
                  <div className="flex items-center gap-2.5 min-w-0">
                    {renderFileIcon(item.mimeType, "h-5 w-5 shrink-0")}
                    <span className="truncate font-medium text-gray-800">
                      {name}
                    </span>
                  </div>
                </td>
                <td className="px-4 py-2.5 text-gray-500 hidden md:table-cell">
                  <div className="flex items-center gap-1.5 min-w-0">
                    <UserCircle2 className="h-4 w-4 text-gray-400 shrink-0" />
                    <span className="truncate">{owner}</span>
                  </div>
                </td>
                <td className="px-4 py-2.5 text-gray-400 hidden sm:table-cell whitespace-nowrap">
                  {date && new Date(date).toLocaleString("vi-VN", {
                    month: "short",
                    day: "numeric",
                    year: "numeric",
                    hour: "2-digit",
                    minute: "2-digit",
                  })}
                </td>
                <td className="px-4 py-2.5 text-gray-400 tabular-nums whitespace-nowrap">
                  {item.type === "folder" || !item.sizeBytes ? "—" : formatBytes(item.sizeBytes)}
                </td>
                <td className="px-1 py-2.5">
                  <FileActionMenu item={item} onAction={onAction} isTrash={isTrash} isViewOnly={isViewOnly} />
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
});

// ─── Grid View ───────────────────────────────────────────────────

const FileGridView = memo(function FileGridView({
  items,
  isLoading,
  onSelect,
  onAction,
  onContextMenu,
  isTrash,
  emptyMessage,
  isViewOnly = false,
}: {
  items: any[];
  isLoading: boolean;
  onSelect: (item: any) => void;
  onAction: (e: React.MouseEvent, action: string, item: any) => void;
  onContextMenu: (e: React.MouseEvent, item: any) => void;
  isTrash: boolean;
  emptyMessage: string;
  isViewOnly?: boolean;
}) {
  if (isLoading) {
    return (
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4">
        {Array.from({ length: 8 }).map((_, i) => (
          <div key={i} className="rounded-xl border border-gray-100 bg-white p-4 space-y-3">
            <div className="flex h-12 items-center justify-center">
              <div className="h-10 w-10 animate-pulse rounded bg-gray-100" />
            </div>
            <div className="h-3.5 w-full animate-pulse rounded bg-gray-100" />
            <div className="h-3 w-1/2 animate-pulse rounded bg-gray-100" />
          </div>
        ))}
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-gray-200 bg-white py-16">
        <FolderOpen className="h-10 w-10 text-gray-300" />
        <p className="text-sm text-gray-400">{emptyMessage}</p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4">
      {items.map((item) => {
        const name = item.name || item.originalName;
        return (
          <div
            key={item.id}
            onClick={() => onSelect(item)}
            onContextMenu={(e) => onContextMenu(e, item)}
            className="group relative rounded-xl border border-gray-100 bg-white p-4 cursor-pointer transition-all space-y-3 hover:border-gray-200 hover:bg-gray-50 hover:shadow-sm"
          >
            <div className="absolute right-2 top-2 opacity-0 group-hover:opacity-100 transition-opacity">
              <FileActionMenu item={item} onAction={onAction} isTrash={isTrash} isViewOnly={isViewOnly} />
            </div>

            <div className="flex h-12 items-center justify-center">
              {renderFileIcon(item.mimeType, "h-10 w-10")}
            </div>

            <div>
              <p className="truncate text-xs font-semibold text-gray-800" title={name}>
                {name}
              </p>
              <p className="mt-0.5 text-[10px] text-gray-400">
                {item.type === "folder" || !item.sizeBytes ? "—" : formatBytes(item.sizeBytes)}
              </p>
            </div>
          </div>
        );
      })}
    </div>
  );
});

// ─── Default export wrapped in Suspense (required for useSearchParams) ───

export default function FilesPage() {
  return (
    <Suspense fallback={<div className="flex h-full items-center justify-center"><div className="h-8 w-8 animate-spin rounded-full border-4 border-[#7C3AED] border-t-transparent" /></div>}>
      <FilesPageInner />
    </Suspense>
  );
}
