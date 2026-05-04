"use client";

import { useState } from "react";
import { Loader2, Trash2, FolderOpen, Info } from "lucide-react";
import { toast } from "sonner";

import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { deleteFolder } from "@/lib/api/folder";
import { AppError } from "@/lib/api/core/api-error";

const FOLDER_NOT_EMPTY_CODE = 1028;

interface DeleteFolderDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  folderId: string;
  folderName: string;
  onSuccess: () => void;
}

export function DeleteFolderDialog({
  open,
  onOpenChange,
  folderId,
  folderName,
  onSuccess,
}: DeleteFolderDialogProps) {
  const [isDeleting, setIsDeleting] = useState(false);

  async function handleDelete() {
    setIsDeleting(true);
    try {
      await deleteFolder(folderId);
      toast.success(`"${folderName}" has been removed`);
      onSuccess();
      onOpenChange(false);
    } catch (error) {
      if (error instanceof AppError && error.status === FOLDER_NOT_EMPTY_CODE) {
        toast.error(
          `Cannot remove "${folderName}" — the folder is not empty. Please remove all files and sub-folders inside first.`,
          { duration: 5000 }
        );
      } else {
        toast.error(error instanceof Error ? error.message : "Failed to remove folder");
      }
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[440px] p-0 overflow-hidden">
        {/* Header */}
        <div className="bg-red-600 px-6 py-5 flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-white/20 shrink-0">
            <Trash2 className="h-5 w-5 text-white" />
          </div>
          <div>
            <DialogHeader>
              <DialogTitle className="text-white text-base font-semibold">
                Remove Folder
              </DialogTitle>
            </DialogHeader>
            <p className="text-red-100 text-xs mt-0.5">This action cannot be undone</p>
          </div>
        </div>

        {/* Body */}
        <div className="px-6 py-5 space-y-4">
          {/* Folder name preview */}
          <div className="flex items-center gap-3 rounded-lg border border-gray-200 bg-gray-50 px-4 py-3">
            <FolderOpen className="h-8 w-8 text-amber-400 shrink-0" />
            <div className="min-w-0">
              <p className="truncate font-semibold text-gray-800 text-sm">{folderName}</p>
              <p className="text-xs text-gray-500">Folder</p>
            </div>
          </div>

          {/* Requirement notice */}
          <div className="rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 space-y-1">
            <p className="flex items-center gap-1.5 text-sm font-semibold text-blue-700">
              <Info className="h-4 w-4 shrink-0" />
              Before you remove
            </p>
            <ul className="space-y-1 text-xs text-blue-600 list-disc list-inside">
              <li>The folder must have <strong>no active files</strong> and <strong>no sub-folders</strong>.</li>
              <li>Files in Trash (<em>Pending Delete / Deleted</em>) are ignored and will not block removal.</li>
            </ul>
          </div>

          <p className="text-sm text-gray-500">
            Are you sure you want to permanently remove{" "}
            <span className="font-semibold text-gray-800">&ldquo;{folderName}&rdquo;</span>?
          </p>
        </div>

        <DialogFooter className="px-6 pb-5 gap-2 sm:gap-2">
          <Button
            variant="outline"
            className="flex-1"
            onClick={() => onOpenChange(false)}
            disabled={isDeleting}
          >
            Cancel
          </Button>
          <Button
            className="flex-1 bg-red-600 hover:bg-red-700 text-white gap-2"
            onClick={handleDelete}
            disabled={isDeleting}
          >
            {isDeleting
              ? <Loader2 className="h-4 w-4 animate-spin" />
              : <Trash2 className="h-4 w-4" />}
            {isDeleting ? "Removing…" : "Remove"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
