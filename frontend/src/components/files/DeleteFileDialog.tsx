"use client";

import { useState } from "react";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { deleteFile, deleteFileDirect } from "@/lib/api/file";
import type { FileMetadataResponse } from "@/types/file/file";

// ─── Props ───────────────────────────────────────────────────────

interface DeleteFileDialogProps {
  file: FileMetadataResponse | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
  isDirectDelete?: boolean;
}

// ─── Component ───────────────────────────────────────────────────

export function DeleteFileDialog({
  file,
  open,
  onOpenChange,
  onSuccess,
  isDirectDelete,
}: DeleteFileDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleDelete() {
    if (!file) return;
    setIsSubmitting(true);
    try {
      if (isDirectDelete) {
        await deleteFileDirect(file.id);
        toast.success(`"${file.originalName}" has been permanently deleted`);
      } else {
        await deleteFile(file.id);
        toast.success(`"${file.originalName}" has been moved to trash`);
      }
      onOpenChange(false);
      onSuccess();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to delete file");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{isDirectDelete ? "Permanently Delete File" : "Delete File"}</AlertDialogTitle>
          <AlertDialogDescription>
            Are you sure you want to delete{" "}
            <span className="font-semibold">{file?.originalName}</span>?
            {isDirectDelete 
              ? " This action cannot be undone and the file will be permanently removed."
              : " The file will be moved to trash and scheduled for permanent deletion."}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={isSubmitting}>Cancel</AlertDialogCancel>
          <AlertDialogAction
            onClick={handleDelete}
            disabled={isSubmitting}
            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
          >
            {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            Delete
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
