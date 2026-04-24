"use client";

import {
  FileImage,
  FileText,
  FileArchive,
  FileVideo,
  FileAudio,
  File as FileIcon,
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { FileMetadataResponse } from "@/types/file/file";
import type { FileSortBy, SortDirection } from "@/lib/constants";
import { FILE_STATUS } from "@/lib/constants";

// ─── Helpers ─────────────────────────────────────────────────────

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

function renderFileIcon(mimeType: string) {
  if (mimeType.startsWith("image/")) return <FileImage className="h-4 w-4 text-sky-500" />;
  if (mimeType.startsWith("video/")) return <FileVideo className="h-4 w-4 text-purple-500" />;
  if (mimeType.startsWith("audio/")) return <FileAudio className="h-4 w-4 text-orange-500" />;
  if (mimeType.includes("zip") || mimeType.includes("tar") || mimeType.includes("rar"))
    return <FileArchive className="h-4 w-4 text-amber-500" />;
  if (mimeType.includes("pdf") || mimeType.includes("document") || mimeType.startsWith("text/"))
    return <FileText className="h-4 w-4 text-blue-500" />;
  return <FileIcon className="h-4 w-4 text-muted-foreground" />;
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

interface SortState {
  sortBy: FileSortBy;
  sortDirection: SortDirection;
}

interface FileTableProps {
  files: FileMetadataResponse[];
  isLoading: boolean;
  sort: SortState;
  onSortChange: (sort: SortState) => void;
  /** Render callback for the trailing actions cell per row */
  renderActions?: (file: FileMetadataResponse) => React.ReactNode;
  emptyMessage?: string;
}

// ─── Skeleton rows ───────────────────────────────────────────────

function SkeletonRows({ count = 5 }: { count?: number }) {
  return (
    <>
      {Array.from({ length: count }).map((_, i) => (
        <TableRow key={i}>
          <TableCell className="px-3 py-2.5">
            <div className="flex items-center gap-2.5">
              <Skeleton className="h-4 w-4 rounded" />
              <Skeleton className="h-4 w-36" />
            </div>
          </TableCell>
          <TableCell className="px-3 py-2.5"><Skeleton className="h-5 w-16 rounded-full" /></TableCell>
          <TableCell className="px-3 py-2.5"><Skeleton className="h-4 w-20" /></TableCell>
          <TableCell className="px-3 py-2.5"><Skeleton className="h-4 w-24" /></TableCell>
          <TableCell className="px-3 py-2.5 text-right"><Skeleton className="ml-auto h-4 w-14" /></TableCell>
          <TableCell className="px-3 py-2.5"><Skeleton className="h-8 w-8 rounded" /></TableCell>
        </TableRow>
      ))}
    </>
  );
}

// ─── Component ───────────────────────────────────────────────────

export function FileTable({
  files,
  isLoading,
  sort,
  onSortChange,
  renderActions,
  emptyMessage = "No files found.",
}: FileTableProps) {
  function handleSort(column: FileSortBy) {
    if (sort.sortBy === column) {
      onSortChange({
        sortBy: column,
        sortDirection: sort.sortDirection === "ASC" ? "DESC" : "ASC",
      });
    } else {
      onSortChange({ sortBy: column, sortDirection: "ASC" });
    }
  }

  function SortIcon({ column }: { column: FileSortBy }) {
    if (sort.sortBy !== column) return <ArrowUpDown className="ml-1 h-3 w-3 opacity-40" />;
    return sort.sortDirection === "ASC"
      ? <ArrowUp className="ml-1 h-3 w-3" />
      : <ArrowDown className="ml-1 h-3 w-3" />;
  }

  return (
    <Table>
      <TableHeader>
        <TableRow className="border-b border-border bg-muted/40 hover:bg-muted/40">
          <TableHead className="h-11 px-3">
            <Button
              variant="ghost"
              size="sm"
              className="-ml-3 h-8 gap-0.5 font-medium"
              onClick={() => handleSort("ORIGINAL_NAME" as FileSortBy)}
            >
              Name
              <SortIcon column={"ORIGINAL_NAME" as FileSortBy} />
            </Button>
          </TableHead>
          <TableHead className="h-11 px-3">Status</TableHead>
          <TableHead className="h-11 px-3">Uploaded By</TableHead>
          <TableHead className="h-11 px-3">
            <Button
              variant="ghost"
              size="sm"
              className="-ml-3 h-8 gap-0.5 font-medium"
              onClick={() => handleSort("CREATED_AT" as FileSortBy)}
            >
              Date
              <SortIcon column={"CREATED_AT" as FileSortBy} />
            </Button>
          </TableHead>
          <TableHead className="h-11 px-3 text-right">
            <Button
              variant="ghost"
              size="sm"
              className="-mr-3 ml-auto h-8 gap-0.5 font-medium"
              onClick={() => handleSort("SIZE_BYTES" as FileSortBy)}
            >
              Size
              <SortIcon column={"SIZE_BYTES" as FileSortBy} />
            </Button>
          </TableHead>
          <TableHead className="h-11 w-[52px] px-3" />
        </TableRow>
      </TableHeader>
      <TableBody>
        {isLoading ? (
          <SkeletonRows />
        ) : files.length === 0 ? (
          <TableRow>
            <TableCell colSpan={6} className="py-12 text-center text-muted-foreground">
              <div className="flex flex-col items-center gap-2">
                <FileIcon className="h-8 w-8 text-muted-foreground/50" />
                <p>{emptyMessage}</p>
              </div>
            </TableCell>
          </TableRow>
        ) : (
          files.map((file) => (
            <TableRow
              key={file.id}
              className="group border-b border-border/50 hover:bg-muted/30 transition-colors"
            >
              <TableCell className="px-3 py-2.5 font-medium">
                <div className="flex items-center gap-2.5">
                  {renderFileIcon(file.mimeType)}
                  <span className="truncate max-w-[240px]" title={file.originalName}>
                    {file.originalName}
                  </span>
                </div>
              </TableCell>
              <TableCell className="px-3 py-2.5">
                <Badge variant="outline" className={STATUS_STYLES[file.status] ?? ""}>
                  {statusLabel(file.status)}
                </Badge>
              </TableCell>
              <TableCell className="px-3 py-2.5 text-sm text-muted-foreground">
                {file.uploadedBy?.fullName ?? "—"}
              </TableCell>
              <TableCell className="px-3 py-2.5 text-sm text-muted-foreground">
                {new Date(file.createdAt).toLocaleDateString()}
              </TableCell>
              <TableCell className="px-3 py-2.5 text-right text-sm tabular-nums text-muted-foreground">
                {formatBytes(file.sizeBytes)}
              </TableCell>
              <TableCell className="px-3 py-2.5">
                {renderActions ? renderActions(file) : null}
              </TableCell>
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
  );
}
