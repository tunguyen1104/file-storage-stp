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
  Folder as FolderIcon,
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
import { NewFileMenu } from "@/components/files/NewFileMenu";
import { useUpload } from "@/contexts/upload-context";
import { listFiles, downloadFile, getPresignedGetUrl } from "@/lib/api/file";
import { listFolderItems, getFolderBreadcrumbs } from "@/lib/api/folder";
import { useDebounce } from "@/hooks/use-debounce";
import { CreateFolderDialog } from "@/components/folders/CreateFolderDialog";
import { RenameFolderDialog } from "@/components/folders/RenameFolderDialog";
import { DeleteFolderDialog } from "@/components/folders/DeleteFolderDialog";
import { MoveItemDialog } from "@/components/files/MoveItemDialog";
import {
  DEFAULT_PAGE_SIZE,
  FILE_STATUS,
  FILE_SORT_BY,
  SORT_DIRECTION,
} from "@/lib/constants";
import type { FileSortBy, SortDirection } from "@/lib/constants";
import type { FileMetadataResponse } from "@/types/file/file";
import type { FolderItem } from "@/types/folder/folder";
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
    return <FileText className={cn(size, "text-red-500")} />;
  return <FileIcon className={cn(size, "text-gray-400")} />;
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

function getTypeName(mimeType: string | null): string {
  if (!mimeType) return "Folder";
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

  const { addFiles } = useUpload();
  const [newFileMenuOpen, setNewFileMenuOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<FileMetadataResponse | null>(null);
  const [selectedItem, setSelectedItem] = useState<any>(null); // FolderItem or FileMetadataResponse
  const [selectedFile, setSelectedFile] = useState<FileMetadataResponse | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [currentFolderId, setCurrentFolderId] = useState<string | null>(null);
  const [createFolderOpen, setCreateFolderOpen] = useState(false);
  const [renameFolderOpen, setRenameFolderOpen] = useState(false);
  const [deleteFolderOpen, setDeleteFolderOpen] = useState(false);
  const [moveFolderOpen, setMoveFolderOpen] = useState(false);
  const [moveFileOpen, setMoveFileOpen] = useState(false);

  useEffect(() => {
    setSelectedFile(null);
    setSelectedItem(null);
    setCurrentPage(1);
    setSearch("");
    setCurrentFolderId(null);
  }, [tab]);

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
    setDeleteTarget(null);
    setSelectedFile(null);
    setSelectedItem(null);
  }

  function handleItemSelect(item: any) {
    if (item.type === "folder") {
      setCurrentFolderId(item.id);
      setCurrentPage(1);
      setSelectedItem(null);
      setSelectedFile(null);
    } else {
      if (selectedItem?.id === item.id) {
        // Deselect
        setSelectedItem(null);
        setSelectedFile(null);
      } else {
        // Map FolderItem → FileMetadataResponse shape instantly (no API call)
        setSelectedItem(item);
        setSelectedFile({
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
            <div className="flex items-center gap-1.5 text-sm overflow-x-auto whitespace-nowrap hide-scrollbar">
              <span
                className="flex items-center gap-1 text-gray-500 hover:text-gray-700 cursor-pointer transition-colors"
                onClick={() => {
                  setCurrentFolderId(null);
                  setCurrentPage(1);
                }}
              >
                <Home className="h-3.5 w-3.5" />
                My Files
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
                    All Files
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

              {/* Choose button (only on All Files, not Trash) */}
              {!isTrash && (
                <div className="relative">
                  <Button
                    ref={newFileButtonRef}
                    size="sm"
                    className="gap-2 bg-[#7C3AED] hover:bg-[#6D28D9] text-white shadow-sm"
                    onClick={() => setNewFileMenuOpen((v) => !v)}
                  >
                    <Plus className="h-4 w-4" />
                    Choose
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
                    onEditFolder={currentFolderId ? () => {
                      setNewFileMenuOpen(false);
                      setRenameFolderOpen(true);
                    } : undefined}
                    onMoveFolder={currentFolderId ? () => {
                      setNewFileMenuOpen(false);
                      setMoveFolderOpen(true);
                    } : undefined}
                    onRemoveFolder={currentFolderId ? () => {
                      setNewFileMenuOpen(false);
                      setDeleteFolderOpen(true);
                    } : undefined}
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
                  <p className="text-xs text-gray-400 mt-0.5">All file types supported · Max 10 GB per file</p>
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
                selectedItemId={selectedItem?.id}
                onSelect={handleItemSelect}
                emptyMessage={emptyLabel}
              />
            ) : (
              <FileGridView
                items={items}
                isLoading={isLoading}
                selectedItemId={selectedItem?.id}
                onSelect={handleItemSelect}
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
                onMove={() => setMoveFileOpen(true)}
              />
            </>
          )}
        </div>
      </div>

      {/* ── Dialogs ── */}
      <CreateFolderDialog
        open={createFolderOpen}
        onOpenChange={setCreateFolderOpen}
        parentId={currentFolderId}
        onSuccess={handleSuccess}
      />
      {currentFolderId && (
        <RenameFolderDialog
          open={renameFolderOpen}
          onOpenChange={setRenameFolderOpen}
          folderId={currentFolderId}
          currentName={currentFolderName}
          onSuccess={handleSuccess}
        />
      )}
      {currentFolderId && (
        <DeleteFolderDialog
          open={deleteFolderOpen}
          onOpenChange={setDeleteFolderOpen}
          folderId={currentFolderId}
          folderName={currentFolderName}
          onSuccess={() => {
            // Navigate to parent folder (one level up via breadcrumbs)
            const parentCrumb = breadcrumbs
              ? breadcrumbs[breadcrumbs.length - 2]
              : null;
            const parentId =
              parentCrumb && parentCrumb.name !== "root"
                ? parentCrumb.id
                : null;
            setCurrentFolderId(parentId);
            setCurrentPage(1);
            mutate();
          }}
        />
      )}
      <MoveItemDialog
        open={moveFolderOpen}
        onOpenChange={setMoveFolderOpen}
        item={currentFolderId ? {
          id: currentFolderId,
          name: currentFolderName,
          type: "FOLDER",
          parentId: currentFolderParentId
        } : null}
        onSuccess={() => {
          setCurrentFolderId(currentFolderParentId);
          setCurrentPage(1);
          mutate();
          mutateBreadcrumbs();
        }}
      />
      <MoveItemDialog
        open={moveFileOpen}
        onOpenChange={setMoveFileOpen}
        item={selectedFile ? {
          id: selectedFile.id,
          name: selectedFile.originalName,
          type: "FILE",
          parentId: selectedFile.folderId
        } : null}
        onSuccess={() => {
          setSelectedFile(null);
          mutate();
        }}
      />
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
  items,
  isLoading,
  selectedItemId,
  onSelect,
  emptyMessage,
}: {
  items: any[];
  isLoading: boolean;
  selectedItemId: string | undefined;
  onSelect: (item: any) => void;
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
          {items.map((item) => {
            const isSelected = selectedItemId === item.id;
            const name = item.name || item.originalName;
            const date = item.updatedAt || item.createdAt;
            return (
              <tr
                key={item.id}
                onClick={() => onSelect(item)}
                className={cn(
                  "group border-b border-gray-50 cursor-pointer transition-colors",
                  isSelected
                    ? "bg-[#F3E8FF]"
                    : "hover:bg-gray-50"
                )}
              >
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2.5">
                    {renderFileIcon(item.mimeType, "h-4 w-4 shrink-0")}
                    <span className={cn(
                      "truncate font-medium",
                      isSelected ? "text-[#7C3AED]" : "text-gray-800"
                    )}>
                      {name}
                    </span>
                  </div>
                </td>
                <td className="px-4 py-3 text-gray-400 hidden sm:table-cell whitespace-nowrap">
                  {date && new Date(date).toLocaleString(undefined, {
                    month: "short",
                    day: "numeric",
                    year: "numeric",
                    hour: "2-digit",
                    minute: "2-digit",
                  })}
                </td>
                <td className="px-4 py-3 text-gray-500 hidden md:table-cell">
                  {getTypeName(item.mimeType)}
                </td>
                <td className="px-4 py-3 text-right text-gray-400 tabular-nums whitespace-nowrap">
                  {item.type === "folder" || !item.sizeBytes ? "—" : formatBytes(item.sizeBytes)}
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
  items,
  isLoading,
  selectedItemId,
  onSelect,
  emptyMessage,
}: {
  items: any[];
  isLoading: boolean;
  selectedItemId: string | undefined;
  onSelect: (item: any) => void;
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
        const isSelected = selectedItemId === item.id;
        const name = item.name || item.originalName;
        return (
          <div
            key={item.id}
            onClick={() => onSelect(item)}
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
}

// ─── Default export wrapped in Suspense (required for useSearchParams) ───

export default function FilesPage() {
  return (
    <Suspense fallback={<div className="flex h-full items-center justify-center"><div className="h-8 w-8 animate-spin rounded-full border-4 border-[#7C3AED] border-t-transparent" /></div>}>
      <FilesPageInner />
    </Suspense>
  );
}
