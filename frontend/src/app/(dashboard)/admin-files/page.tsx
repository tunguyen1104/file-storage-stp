"use client";

import { useState, useEffect } from "react";
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
} from "lucide-react";

import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
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
import { FileDetailCard } from "@/components/files/FileDetailCard";
import { getAdminFolderItems, getFolderBreadcrumbs } from "@/lib/api/folder";
import { useDebounce } from "@/hooks/use-debounce";
import { DEFAULT_PAGE_SIZE, FILE_SORT_BY, SORT_DIRECTION } from "@/lib/constants";
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

export default function AdminFilesPage() {
  const [currentPage, setCurrentPage] = useState(1);
  const [search, setSearch] = useState("");
  const [currentFolderId, setCurrentFolderId] = useState<string | null>(null);
  const [selectedFile, setSelectedFile] = useState<FileMetadataResponse | null>(null);

  const [filterType, setFilterType] = useState<string>("ALL");
  const [filterStatus, setFilterStatus] = useState<string>("ALL");
  const [sortBy, setSortBy] = useState<string>("CREATED_AT");
  const [sortDirection, setSortDirection] = useState<"ASC" | "DESC">("DESC");

  const debouncedSearch = useDebounce(search, 400);

  const handleSort = (column: string) => {
    if (sortBy === column) {
      setSortDirection(sortDirection === "ASC" ? "DESC" : "ASC");
    } else {
      setSortBy(column);
      setSortDirection("ASC");
    }
  };

  const SortIcon = ({ column }: { column: string }) => {
    if (sortBy !== column) return null;
    return sortDirection === "ASC" ? <ArrowUp className="w-3 h-3 ml-1" /> : <ArrowDown className="w-3 h-3 ml-1" />;
  };

  // SWR for breadcrumbs (only if we are inside a folder)
  const { data: breadcrumbs } = useSWR(
    currentFolderId ? ["admin-breadcrumbs", currentFolderId] : null,
    ([, id]) => getFolderBreadcrumbs(id),
    { revalidateOnFocus: false }
  );

  // SWR for fetching admin items
  const swrKey = ["admin-items", currentPage, debouncedSearch, currentFolderId, filterType, filterStatus, sortBy, sortDirection] as const;
  const { data, error, isLoading, mutate } = useSWR(
    swrKey,
    async ([, page, q, fId, fType, fStatus, sBy, sDir]) => {
      return getAdminFolderItems({
        page,
        limit: DEFAULT_PAGE_SIZE,
        search: q || undefined,
        parentId: fId || undefined,
        type: fType !== "ALL" ? (fType as any) : undefined,
        status: fStatus !== "ALL" ? fStatus : undefined,
        sortBy: sBy as any,
        sortDirection: sDir as any,
      });
    },
    { revalidateOnFocus: false }
  );

  const items = data?.data ?? [];
  const totalPages = data?.totalPages ?? 1;

  function handleItemSelect(item: any) {
    if (item.type === "folder") {
      setCurrentFolderId(item.id);
      setCurrentPage(1);
      setSelectedFile(null);
    } else {
      if (selectedFile?.id === item.id) {
        setSelectedFile(null);
      } else {
        setSelectedFile({
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
        });
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
                <h1 className="text-sm font-semibold text-gray-900">Admin File Explorer</h1>
                <p className="text-xs text-gray-500">Global view across all tenants</p>
            </div>
        </div>
        <div className="flex flex-col sm:flex-row items-center gap-3 w-full sm:w-auto">
          <Select value={filterType} onValueChange={(v) => { setFilterType(v); setCurrentPage(1); }}>
            <SelectTrigger className="w-full sm:w-[130px] h-9 text-sm">
              <SelectValue placeholder="Type" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Types</SelectItem>
              <SelectItem value="FOLDER">Folder</SelectItem>
              <SelectItem value="FILE">File</SelectItem>
            </SelectContent>
          </Select>

          <Select value={filterStatus} onValueChange={(v) => { setFilterStatus(v); setCurrentPage(1); }}>
            <SelectTrigger className="w-full sm:w-[150px] h-9 text-sm">
              <SelectValue placeholder="Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Status</SelectItem>
              <SelectItem value="ACTIVE">Active</SelectItem>
              <SelectItem value="PENDING">Pending</SelectItem>
              <SelectItem value="SCANNING">Scanning</SelectItem>
              <SelectItem value="INFECTED">Infected</SelectItem>
              <SelectItem value="PENDING_DELETE">Pending Delete</SelectItem>
              <SelectItem value="DELETED">Deleted</SelectItem>
              <SelectItem value="UPLOAD_FAILED">Upload Failed</SelectItem>
            </SelectContent>
          </Select>

          <div className="relative w-full sm:w-[250px]">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
            <Input
              className="h-9 border-gray-200 bg-gray-50 pl-9 text-sm placeholder:text-gray-400 focus-visible:ring-primary/30 w-full"
              placeholder="Search across tenants..."
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
                Global Root
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

            <div className="overflow-hidden rounded-xl border border-gray-200 bg-white">
              <table className="w-full text-sm">
                <thead className="border-b border-gray-100 bg-gray-50">
                  <tr>
                    <th className={cn("px-4 py-3 text-left text-xs font-semibold cursor-pointer hover:bg-gray-100 select-none transition-colors", sortBy === "NAME" ? "text-primary" : "text-gray-500")} onClick={() => handleSort("NAME")}>
                      <div className="flex items-center">Name <SortIcon column="NAME" /></div>
                    </th>
                    <th className={cn("px-4 py-3 text-left text-xs font-semibold cursor-pointer hover:bg-gray-100 select-none transition-colors", sortBy === "TENANT_NAME" ? "text-primary" : "text-gray-500")} onClick={() => handleSort("TENANT_NAME")}>
                      <div className="flex items-center">Tenant <SortIcon column="TENANT_NAME" /></div>
                    </th>
                    <th className={cn("px-4 py-3 text-left text-xs font-semibold hidden sm:table-cell cursor-pointer hover:bg-gray-100 select-none transition-colors", sortBy === "CREATED_AT" ? "text-primary" : "text-gray-500")} onClick={() => handleSort("CREATED_AT")}>
                      <div className="flex items-center">Date modified <SortIcon column="CREATED_AT" /></div>
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 hidden md:table-cell">Type</th>
                    <th className={cn("px-4 py-3 text-right text-xs font-semibold cursor-pointer hover:bg-gray-100 select-none transition-colors", sortBy === "SIZE_BYTES" ? "text-primary" : "text-gray-500")} onClick={() => handleSort("SIZE_BYTES")}>
                      <div className="flex items-center justify-end">Size <SortIcon column="SIZE_BYTES" /></div>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {isLoading ? (
                    Array.from({ length: 6 }).map((_, i) => (
                      <tr key={i} className="border-b border-gray-50">
                        <td className="px-4 py-3"><div className="h-4 w-40 animate-pulse rounded bg-gray-100" /></td>
                        <td className="px-4 py-3"><div className="h-4 w-24 animate-pulse rounded bg-gray-100" /></td>
                        <td className="px-4 py-3"><div className="h-4 w-28 animate-pulse rounded bg-gray-100" /></td>
                        <td className="px-4 py-3"><div className="h-4 w-16 animate-pulse rounded bg-gray-100" /></td>
                        <td className="px-4 py-3 text-right"><div className="ml-auto h-4 w-14 animate-pulse rounded bg-gray-100" /></td>
                      </tr>
                    ))
                  ) : items.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="px-4 py-16 text-center text-gray-400">
                        <FolderOpen className="h-10 w-10 text-gray-300 mx-auto mb-3" />
                        No files or folders found.
                      </td>
                    </tr>
                  ) : (
                    items.map((item) => {
                      const isSelected = selectedFile?.id === item.id;
                      const name = item.name || (item as any).originalName;
                      const date = item.updatedAt || item.createdAt;
                      return (
                        <tr
                          key={item.id}
                          onClick={() => handleItemSelect(item)}
                          className={cn(
                            "group border-b border-gray-50 cursor-pointer transition-colors",
                            isSelected
                              ? "bg-primary/10"
                              : "hover:bg-gray-50"
                          )}
                        >
                          <td className="px-4 py-3">
                            <div className="flex items-center gap-2.5">
                              {renderFileIcon(item.mimeType, "h-4 w-4 shrink-0")}
                              <span className={cn(
                                "truncate font-medium",
                                isSelected ? "text-primary" : "text-gray-800"
                              )}>
                                {name}
                              </span>
                            </div>
                          </td>
                          <td className="px-4 py-3 text-gray-500 text-xs">
                            {item.tenantName || <span className="text-gray-300">—</span>}
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
                    })
                  )}
                </tbody>
              </table>
            </div>

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
              {/* Note: In read-only mode, we can still use FileDetailCard. 
                  It will not show delete buttons because `onDelete` is undefined and mode isn't 'trash'/'default' with proper permissions. */}
              <FileDetailCard
                file={selectedFile}
                mode="default"
                onMutate={() => mutate()}
              />
            </>
          )}
        </div>
      </div>
    </div>
  );
}
