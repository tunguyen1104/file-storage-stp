"use client";

import { MoreHorizontal, Eye, Download, RotateCcw, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { FileMetadataResponse } from "@/types/file/file";
import { FILE_STATUS } from "@/lib/constants";

// ─── Props ───────────────────────────────────────────────────────

interface FileActionsMenuProps {
  file: FileMetadataResponse;
  onPreview?: (file: FileMetadataResponse) => void;
  onDownload?: (file: FileMetadataResponse) => void;
  onRestore?: (file: FileMetadataResponse) => void;
  onDelete?: (file: FileMetadataResponse) => void;
}

// ─── Component ───────────────────────────────────────────────────

export function FileActionsMenu({
  file,
  onPreview,
  onDownload,
  onRestore,
  onDelete,
}: FileActionsMenuProps) {
  const isActive = file.status === FILE_STATUS.ACTIVE;
  const isRestorable =
    file.status === FILE_STATUS.PENDING_DELETE || file.status === FILE_STATUS.DELETED;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="h-8 w-8 opacity-0 transition-opacity group-hover:opacity-100 data-[state=open]:opacity-100"
        >
          <MoreHorizontal className="h-4 w-4" />
          <span className="sr-only">Open file actions</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-44">
        {isActive && onPreview && (
          <DropdownMenuItem onClick={() => onPreview(file)}>
            <Eye className="mr-2 h-4 w-4" />
            Preview
          </DropdownMenuItem>
        )}
        {isActive && onDownload && (
          <DropdownMenuItem onClick={() => onDownload(file)}>
            <Download className="mr-2 h-4 w-4" />
            Download
          </DropdownMenuItem>
        )}
        {isRestorable && onRestore && (
          <DropdownMenuItem onClick={() => onRestore(file)}>
            <RotateCcw className="mr-2 h-4 w-4" />
            Restore
          </DropdownMenuItem>
        )}
        {(isActive || isRestorable) && onDelete && <DropdownMenuSeparator />}
        {onDelete && file.status !== FILE_STATUS.DELETED && (
          <DropdownMenuItem
            className="text-destructive focus:text-destructive"
            onClick={() => onDelete(file)}
          >
            <Trash2 className="mr-2 h-4 w-4" />
            Delete
          </DropdownMenuItem>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
