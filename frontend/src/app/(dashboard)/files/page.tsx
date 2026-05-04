"use client";

import { useState, useCallback, useRef, Suspense, useEffect } from "react";
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
  Star,
  ChevronRight,
  Home,
  X,
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
import { FileDetailCard } from "@/components/files/FileDetailCard";
import { DeleteFileDialog } from "@/components/files/DeleteFileDialog";
import { UploadFileDialog } from "@/components/upload/UploadFileDialog";
import { NewFileMenu } from "@/components/files/NewFileMenu";
import { listFiles, downloadFile, getPresignedGetUrl } from "@/lib/api/file";
import { useDebounce } from "@/hooks/use-debounce";
import {
  DEFAULT_PAGE_SIZE,
  FILE_STATUS,
  FILE_SORT_BY,
  SORT_DIRECTION,
} from "@/lib/constants";
import type { FileSortBy, SortDirection } from "@/lib/constants";
import type { FileMetadataResponse } from "@/types/file/file";
import { cn } from "@/lib/utils";

// ─── File icon helpers ───────────────────────────────────────────

function renderFileIcon(mimeType: string, size = "h-5 w-5") {
  if (mimeType.startsWith("image/")) return <FileImage className={cn(size, "text-sky-500")} />;
  if (mimeType.startsWith("video/")) return <FileVideo className={cn(size, "text-purple-500")} />;
  if (mimeType.startsWith("audio/")) return <FileAudio className={cn(size, "text-orange-500")} />;
  if (mimeType.includes("zip") || mimeType.includes("tar") || mimeType.includes("rar"))
    return <FileArchive className={cn(size, "text-amber-500")} />;
  if (mimeType.includes("pdf") || mimeType.includes("document") || mimeType.startsWith("text/"))
    return <FileText className={cn(size, "text-red-500")} />;
  return <FileIcon className={cn(size, "text-gray-400")} />;
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

function getTypeName(mimeType: string): string {
  if (mimeType.includes("pdf")) return "PDF";
  if (mimeType.includes("spreadsheet") || mimeType.includes("excel") || mimeType.includes("xlsx")) return "Excel";
  if (mimeType.includes("document") || mimeType.includes("word") || mimeType.includes("docx")) return "Doc";
  if (mimeType.includes("presentation") || mimeType.includes("powerpoint")) return "PPT";
  if (mimeType.startsWith("image/")) return mimeType.split("/")[1]?.toUpperCase() ?? "Image";
  if (mimeType.startsWith("video/")) return "Video";
  if (mimeType.startsWith("audio/")) return "Audio";
  if (mimeType.includes("zip") || mimeType.includes("tar")) return "Archive";
  if (mimeType === "application/octet-stream") return "Binary";
  return mimeType.split("/")[1] ?? "File";
}

// ─── Inner page (uses useSearchParams inside Suspense) ───────────

function FilesPageInner() {
  const searchParams = useSearchParams();
  const tab = searchParams.get("tab"); // null | "trash" | "shared"
  const isTrash = tab === "trash";

  // Determine status filter based on tab
  const statusFilter = isTrash ? FILE_STATUS.PENDING_DELETE : FILE_STATUS.ACTIVE;
  const pageLabel = isTrash ? "Trash" : "All Files";
  const emptyLabel = isTrash
    ? "No files in trash."
    : "No files yet. Upload one to get started.";

  // ── State ──
  const [currentPage, setCurrentPage] = useState(1);
  const [search, setSearch] = useState("");
  const [viewMode, setViewMode] = useState<"list" | "grid">("list");
  const [sort] = useState<{ sortBy: FileSortBy; sortDirection: SortDirection }>({
    sortBy: FILE_SORT_BY.CREATED_AT,
    sortDirection: SORT_DIRECTION.DESC,
  });
  const debouncedSearch = useDebounce(search, 400);

  const [uploadOpen, setUploadOpen] = useState(false);
  const [newFileMenuOpen, setNewFileMenuOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<FileMetadataResponse | null>(null);
  const [selectedFile, setSelectedFile] = useState<FileMetadataResponse | null>(null);
  const [droppedFile, setDroppedFile] = useState<File | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Reset selected file, page, and search when tab changes
  useEffect(() => {
    setSelectedFile(null);
    setCurrentPage(1);
    setSearch("");
  }, [tab]);

  const newFileButtonRef = useRef<HTMLButtonElement>(null);

  // ── Data ──
  const swrKey = ["files", currentPage, debouncedSearch, statusFilter, sort.sortBy, sort.sortDirection, tab] as const;
  const { data, error, isLoading, mutate } = useSWR(
    swrKey,
    ([, page, q, status, sortBy, sortDirection]) =>
      listFiles({
        page,
        limit: DEFAULT_PAGE_SIZE,
        search: q || undefined,
        status,
        sortBy,
        sortDirection,
      })
  );

  const files = data?.data ?? [];
  const totalPages = data?.totalPages ?? 1;

  function handleSuccess() {
    mutate();
    setDeleteTarget(null);
    setSelectedFile(null);
  }

  function handleFileSelect(file: FileMetadataResponse) {
    setSelectedFile((prev) => (prev?.id === file.id ? null : file));
  }

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
      toast.error(err instanceof Error ? err.message : "Failed to get preview URL");
    }
  }, []);

  // ── Drag & drop ──
  const [isDragging, setIsDragging] = useState(false);

  return (
    <div className="flex h-full flex-col overflow-hidden bg-gray-50">
      {/* ── Top search bar ── */}
      <div className="flex items-center border-b border-gray-200 bg-white px-6 py-3 shrink-0">
        <div className="relative flex-1 max-w-lg">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
          <Input
            className="h-9 border-gray-200 bg-gray-50 pl-9 text-sm placeholder:text-gray-400 focus-visible:ring-[#7C3AED]/30"
            placeholder="Search files, folder"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setCurrentPage(1);
            }}
          />
        </div>
      </div>

      {/* ── Main content area ── */}
      <div className="flex flex-1 overflow-hidden min-h-0">
        {/* ─────────── File list column ─────────── */}
        <div className={cn(
          "flex flex-col overflow-hidden transition-all duration-300 min-w-0",
          selectedFile ? "flex-1" : "flex-1"
        )}>
          {/* Breadcrumb + toolbar */}
          <div className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-3 shrink-0">
            <div className="flex items-center gap-1.5 text-sm">
              <span className="flex items-center gap-1 text-gray-500 hover:text-gray-700 cursor-pointer transition-colors">
                <Home className="h-3.5 w-3.5" />
                My Files
              </span>
              <ChevronRight className="h-3.5 w-3.5 text-gray-400" />
              <span className="font-semibold text-gray-800">{pageLabel}</span>
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

              {/* New File button (only on All Files, not Trash) */}
              {!isTrash && (
                <div className="relative">
                  <Button
                    ref={newFileButtonRef}
                    size="sm"
                    className="gap-2 bg-[#7C3AED] hover:bg-[#6D28D9] text-white shadow-sm"
                    onClick={() => setNewFileMenuOpen((v) => !v)}
                  >
                    <Plus className="h-4 w-4" />
                    New File
                  </Button>
                  <NewFileMenu
                    open={newFileMenuOpen}
                    onClose={() => setNewFileMenuOpen(false)}
                    anchorRef={newFileButtonRef}
                    onUpload={() => {
                      setNewFileMenuOpen(false);
                      setUploadOpen(true);
                    }}
                    onCreateFolder={() => {
                      setNewFileMenuOpen(false);
                      toast.info("Create folder coming soon");
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
                  {error instanceof Error ? error.message : "Failed to load files"}
                </span>
                <Button
                  size="sm"
                  variant="outline"
                  className="gap-1.5 text-red-600 hover:text-red-700 border-red-200"
                  onClick={() => mutate()}
                >
                  <RefreshCw className="h-3.5 w-3.5" />
                  Retry
                </Button>
              </div>
            )}

            {/* Upload drop zone (only on All Files) */}
            {!isTrash && (
              <div
                onDragOver={(e) => { e.preventDefault(); setIsDragging(true); }}
                onDragLeave={() => setIsDragging(false)}
                onDrop={(e) => {
                  e.preventDefault();
                  setIsDragging(false);
                  const file = e.dataTransfer.files[0];
                  if (file) {
                    setDroppedFile(file);
                    setUploadOpen(true);
                  }
                }}
                onClick={() => fileInputRef.current?.click()}
                className={cn(
                  "flex flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed py-8 cursor-pointer transition-all",
                  isDragging
                    ? "border-[#7C3AED] bg-[#F3E8FF]/40"
                    : "border-gray-200 bg-white hover:border-[#7C3AED]/50 hover:bg-gray-50"
                )}
              >
                <div className={cn(
                  "flex h-12 w-12 items-center justify-center rounded-full transition-colors",
                  isDragging ? "bg-[#7C3AED]/10" : "bg-gray-100"
                )}>
                  <CloudUpload className={cn("h-6 w-6 transition-colors", isDragging ? "text-[#7C3AED]" : "text-gray-400")} />
                </div>
                <div className="text-center">
                  <p className="text-sm font-medium text-gray-700">Click or drag files to this area to upload</p>
                  <p className="text-xs text-gray-400 mt-0.5">Support for PDF, Images, Zip and Docs up to 500MB</p>
                </div>
              </div>
            )}

            {/* Hidden file input for outer drop zone */}
            {!isTrash && (
              <input
                type="file"
                className="hidden"
                ref={fileInputRef}
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) {
                    setDroppedFile(file);
                    setUploadOpen(true);
                  }
                  e.target.value = '';
                }}
              />
            )}

            {/* File list / grid */}
            {viewMode === "list" ? (
              <FileListView
                files={files}
                isLoading={isLoading}
                selectedFile={selectedFile}
                onSelect={handleFileSelect}
                emptyMessage={emptyLabel}
              />
            ) : (
              <FileGridView
                files={files}
                isLoading={isLoading}
                selectedFile={selectedFile}
                onSelect={handleFileSelect}
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
              <div className="absolute" style={{ right: 0, top: 0 }} />
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
                mode={isTrash ? "trash" : "default"}
                onMutate={handleSuccess}
                onDelete={(f) => setDeleteTarget(f)}
              />
            </>
          )}
        </div>
      </div>

      {/* ── Dialogs ── */}
      {!isTrash && (
        <UploadFileDialog
          open={uploadOpen}
          onOpenChange={(open) => {
            setUploadOpen(open);
            if (!open) setDroppedFile(null);
          }}
          onSuccess={handleSuccess}
          initialFile={droppedFile}
        />
      )}
      <DeleteFileDialog
        file={deleteTarget}
        open={deleteTarget !== null}
        onOpenChange={(open) => { if (!open) setDeleteTarget(null); }}
        onSuccess={handleSuccess}
        isDirectDelete={isTrash}
      />
    </div>
  );
}

// ─── List View ───────────────────────────────────────────────────

function FileListView({
  files,
  isLoading,
  selectedFile,
  onSelect,
  emptyMessage,
}: {
  files: FileMetadataResponse[];
  isLoading: boolean;
  selectedFile: FileMetadataResponse | null;
  onSelect: (f: FileMetadataResponse) => void;
  emptyMessage: string;
}) {
  if (isLoading) {
    return (
      <div className="overflow-hidden rounded-xl border border-gray-200 bg-white">
        <table className="w-full text-sm">
          <thead className="border-b border-gray-100 bg-gray-50">
            <tr>
              <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500">Name</th>
              <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500">Date modified</th>
              <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500">Type</th>
              <th className="px-4 py-3 text-right text-xs font-semibold text-gray-500">Size</th>
            </tr>
          </thead>
          <tbody>
            {Array.from({ length: 6 }).map((_, i) => (
              <tr key={i} className="border-b border-gray-50">
                <td className="px-4 py-3"><div className="h-4 w-40 animate-pulse rounded bg-gray-100" /></td>
                <td className="px-4 py-3"><div className="h-4 w-28 animate-pulse rounded bg-gray-100" /></td>
                <td className="px-4 py-3"><div className="h-4 w-16 animate-pulse rounded bg-gray-100" /></td>
                <td className="px-4 py-3 text-right"><div className="ml-auto h-4 w-14 animate-pulse rounded bg-gray-100" /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  }

  if (files.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-gray-200 bg-white py-16">
        <FolderOpen className="h-10 w-10 text-gray-300" />
        <p className="text-sm text-gray-400">{emptyMessage}</p>
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-xl border border-gray-200 bg-white">
      <table className="w-full text-sm">
        <thead className="border-b border-gray-100 bg-gray-50">
          <tr>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500">Name</th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 hidden sm:table-cell">Date modified</th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 hidden md:table-cell">Type</th>
            <th className="px-4 py-3 text-right text-xs font-semibold text-gray-500">Size</th>
          </tr>
        </thead>
        <tbody>
          {files.map((file) => {
            const isSelected = selectedFile?.id === file.id;
            return (
              <tr
                key={file.id}
                onClick={() => onSelect(file)}
                className={cn(
                  "group border-b border-gray-50 cursor-pointer transition-colors",
                  isSelected
                    ? "bg-[#F3E8FF]"
                    : "hover:bg-gray-50"
                )}
              >
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2.5">
                    {renderFileIcon(file.mimeType, "h-4 w-4 shrink-0")}
                    <span className={cn(
                      "truncate font-medium",
                      isSelected ? "text-[#7C3AED]" : "text-gray-800"
                    )}>
                      {file.originalName}
                    </span>
                  </div>
                </td>
                <td className="px-4 py-3 text-gray-400 hidden sm:table-cell whitespace-nowrap">
                  {new Date(file.updatedAt ?? file.createdAt).toLocaleString(undefined, {
                    month: "short",
                    day: "numeric",
                    year: "numeric",
                    hour: "2-digit",
                    minute: "2-digit",
                  })}
                </td>
                <td className="px-4 py-3 text-gray-500 hidden md:table-cell">
                  {getTypeName(file.mimeType)}
                </td>
                <td className="px-4 py-3 text-right text-gray-400 tabular-nums whitespace-nowrap">
                  {file.sizeBytes > 0 ? formatBytes(file.sizeBytes) : "—"}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

// ─── Grid View ───────────────────────────────────────────────────

function FileGridView({
  files,
  isLoading,
  selectedFile,
  onSelect,
  emptyMessage,
}: {
  files: FileMetadataResponse[];
  isLoading: boolean;
  selectedFile: FileMetadataResponse | null;
  onSelect: (f: FileMetadataResponse) => void;
  emptyMessage: string;
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

  if (files.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-gray-200 bg-white py-16">
        <FolderOpen className="h-10 w-10 text-gray-300" />
        <p className="text-sm text-gray-400">{emptyMessage}</p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4">
      {files.map((file) => {
        const isSelected = selectedFile?.id === file.id;
        return (
          <div
            key={file.id}
            onClick={() => onSelect(file)}
            className={cn(
              "group relative rounded-xl border p-4 cursor-pointer transition-all space-y-3",
              isSelected
                ? "border-[#7C3AED] bg-[#F3E8FF]/50 shadow-sm"
                : "border-gray-100 bg-white hover:border-gray-200 hover:shadow-sm"
            )}
          >
            <button
              className="absolute right-3 top-3 opacity-0 group-hover:opacity-100 transition-opacity"
              onClick={(e) => e.stopPropagation()}
            >
              <Star className="h-3.5 w-3.5 text-gray-300 hover:text-amber-400 transition-colors" />
            </button>

            <div className="flex h-12 items-center justify-center">
              {renderFileIcon(file.mimeType, "h-10 w-10")}
            </div>

            <div>
              <p className="truncate text-xs font-semibold text-gray-800">
                {file.originalName}
              </p>
              <p className="mt-0.5 text-[10px] text-gray-400">{formatBytes(file.sizeBytes)}</p>
            </div>
          </div>
        );
      })}
    </div>
  );
}

// ─── Default export wrapped in Suspense (required for useSearchParams) ───

export default function FilesPage() {
  return (
    <Suspense fallback={<div className="flex h-full items-center justify-center"><div className="h-8 w-8 animate-spin rounded-full border-4 border-[#7C3AED] border-t-transparent" /></div>}>
      <FilesPageInner />
    </Suspense>
  );
}
