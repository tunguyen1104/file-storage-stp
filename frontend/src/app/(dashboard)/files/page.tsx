"use client";

import { useState, useCallback } from "react";
import { useAuth } from "@/hooks/use-auth";
import { ROLES } from "@/lib/constants";
import useSWR from "swr";
import {
  AlertCircle,
  RefreshCw,
  Search,
  Upload,
  X,
} from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
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
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { FileTable } from "@/components/files/FileTable";
import { FileActionsMenu } from "@/components/files/FileActionsMenu";
import { FileDetailCard } from "@/components/files/FileDetailCard";
import { DeleteFileDialog } from "@/components/files/DeleteFileDialog";
import { UploadFileDialog } from "@/components/upload/UploadFileDialog";
import { listFiles, downloadFile, getPresignedGetUrl, restoreFile } from "@/lib/api/file";
import { useDebounce } from "@/hooks/use-debounce";
import {
  DEFAULT_PAGE_SIZE,
  FILE_STATUS,
  FILE_SORT_BY,
  SORT_DIRECTION,
} from "@/lib/constants";
import type { FileStatus, FileSortBy, SortDirection } from "@/lib/constants";
import type { FileMetadataResponse } from "@/types/file/file";

// ─── Status filter options ───────────────────────────────────────

const STATUS_OPTIONS: { label: string; value: FileStatus }[] = [
  { label: "Active", value: FILE_STATUS.ACTIVE },
  { label: "Pending", value: FILE_STATUS.PENDING },
  { label: "Pending Delete", value: FILE_STATUS.PENDING_DELETE },
  { label: "Deleted", value: FILE_STATUS.DELETED },
  { label: "Upload Failed", value: FILE_STATUS.UPLOAD_FAILED },
];

// ─── Page component ──────────────────────────────────────────────

export default function FilesPage() {
  // ── Filter / sort state ──
  const [currentPage, setCurrentPage] = useState(1);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<FileStatus | "ALL">("ALL");
  const [sort, setSort] = useState<{ sortBy: FileSortBy; sortDirection: SortDirection }>({
    sortBy: FILE_SORT_BY.CREATED_AT,
    sortDirection: SORT_DIRECTION.DESC,
  });

  const debouncedSearch = useDebounce(search, 400);

  // ── Dialog state ──
  const [uploadOpen, setUploadOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<FileMetadataResponse | null>(null);
  const [previewTarget, setPreviewTarget] = useState<FileMetadataResponse | null>(null);

  // ── Data fetching ──
  const swrKey = [
    "files",
    currentPage,
    debouncedSearch,
    statusFilter,
    sort.sortBy,
    sort.sortDirection,
  ] as const;

  const { data, error, isLoading, mutate } = useSWR(
    swrKey,
    ([, page, q, status, sortBy, sortDirection]) =>
      listFiles({
        page,
        limit: DEFAULT_PAGE_SIZE,
        search: q || undefined,
        status: status === "ALL" ? undefined : status,
        sortBy,
        sortDirection,
      })
  );

  const files = data?.data ?? [];
  const totalPages = data?.totalPages ?? 1;

  // ── Handlers ──
  function handleSearchChange(value: string) {
    setSearch(value);
    setCurrentPage(1);
  }

  function handleStatusChange(value: string) {
    setStatusFilter(value as FileStatus | "ALL");
    setCurrentPage(1);
  }

  function handleSortChange(newSort: { sortBy: FileSortBy; sortDirection: SortDirection }) {
    setSort(newSort);
    setCurrentPage(1);
  }

  function handleSuccess() {
    mutate();
  }

  const handlePreview = useCallback(async (file: FileMetadataResponse) => {
    try {
      const { getUrl } = await getPresignedGetUrl(file.id);
      window.open(getUrl, "_blank", "noopener,noreferrer");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to get preview URL");
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
      toast.error(err instanceof Error ? err.message : "Failed to download file");
    }
  }, []);

  const handleRestore = useCallback(async (file: FileMetadataResponse) => {
    try {
      await restoreFile(file.id);
      toast.success(`"${file.originalName}" has been restored`);
      mutate();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to restore file");
    }
  }, [mutate]);

  return (
    <section className="space-y-4">
      {/* ── Toolbar ── */}
      <div className="flex flex-col gap-3 rounded-lg border border-border bg-card p-4 md:flex-row md:items-center md:justify-between">
        <div className="flex flex-1 flex-col gap-2 sm:flex-row sm:items-center">
          {/* Search */}
          <div className="relative w-full sm:max-w-sm">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="h-10 pl-9 pr-9"
              placeholder="Search files..."
              value={search}
              onChange={(e) => handleSearchChange(e.target.value)}
            />
            {search && (
              <button
                type="button"
                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                onClick={() => handleSearchChange("")}
              >
                <X className="h-3.5 w-3.5" />
                <span className="sr-only">Clear search</span>
              </button>
            )}
          </div>

          {/* Status filter */}
          <Select value={statusFilter} onValueChange={handleStatusChange}>
            <SelectTrigger className="h-10 w-full sm:w-[160px]">
              <SelectValue placeholder="All statuses" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Statuses</SelectItem>
              {STATUS_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <Button className="gap-2" onClick={() => setUploadOpen(true)}>
          <Upload className="h-4 w-4" />
          Upload File
        </Button>
      </div>

      {/* ── Error state ── */}
      {error && !isLoading && (
        <div className="flex items-center gap-3 rounded-lg border border-destructive/50 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span className="flex-1">
            {error instanceof Error ? error.message : "Failed to load files"}
          </span>
          <Button
            size="sm"
            variant="outline"
            className="gap-1.5 text-destructive hover:text-destructive"
            onClick={() => mutate()}
          >
            <RefreshCw className="h-3.5 w-3.5" />
            Retry
          </Button>
        </div>
      )}

      {/* ── File table ── */}
      <div className="rounded-lg border border-border bg-card">
        <FileTable
          files={files}
          isLoading={isLoading}
          sort={sort}
          onSortChange={handleSortChange}
          emptyMessage={
            debouncedSearch
              ? `No files found for "${debouncedSearch}"`
              : "No files found. Upload a file to get started."
          }
          renderActions={(file) => (
            <FileActionsMenu
              file={file}
              onPreview={handlePreview}
              onDownload={handleDownload}
              onRestore={handleRestore}
              onDelete={(f) => setDeleteTarget(f)}
            />
          )}
        />

        {/* ── Pagination ── */}
        {totalPages > 1 && (
          <div className="border-t border-border px-4 py-3">
            <Pagination>
              <PaginationContent>
                <PaginationItem>
                  <PaginationPrevious
                    href="#"
                    onClick={(e) => {
                      e.preventDefault();
                      setCurrentPage((p) => Math.max(1, p - 1));
                    }}
                    className={currentPage === 1 ? "pointer-events-none opacity-50" : ""}
                  />
                </PaginationItem>

                {Array.from({ length: totalPages }, (_, i) => i + 1).map((page) => (
                  <PaginationItem key={page}>
                    <PaginationLink
                      href="#"
                      isActive={page === currentPage}
                      onClick={(e) => {
                        e.preventDefault();
                        setCurrentPage(page);
                      }}
                    >
                      {page}
                    </PaginationLink>
                  </PaginationItem>
                ))}

                <PaginationItem>
                  <PaginationNext
                    href="#"
                    onClick={(e) => {
                      e.preventDefault();
                      setCurrentPage((p) => Math.min(totalPages, p + 1));
                    }}
                    className={currentPage === totalPages ? "pointer-events-none opacity-50" : ""}
                  />
                </PaginationItem>
              </PaginationContent>
            </Pagination>
          </div>
        )}
      </div>

      {/* ── File detail sheet (preview panel) ── */}
      <Sheet open={previewTarget !== null} onOpenChange={(open) => { if (!open) setPreviewTarget(null); }}>
        <SheetContent className="sm:max-w-lg overflow-y-auto">
          <SheetHeader>
            <SheetTitle>File Details</SheetTitle>
          </SheetHeader>
          <div className="mt-4">
            <FileDetailCard file={previewTarget} onMutate={handleSuccess} />
          </div>
        </SheetContent>
      </Sheet>

      <UploadFileDialog
        open={uploadOpen}
        onOpenChange={setUploadOpen}
        onSuccess={handleSuccess}
      />

      <DeleteFileDialog
        file={deleteTarget}
        open={deleteTarget !== null}
        onOpenChange={(open) => { if (!open) setDeleteTarget(null); }}
        onSuccess={handleSuccess}
      />
    </section>
  );
}
