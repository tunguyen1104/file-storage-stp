"use client";

import { useCallback, useRef, useState, useEffect } from "react";
import { CloudUpload, File as FileIcon, Loader2, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Progress } from "@/components/ui/progress";
import { uploadSingle } from "@/lib/api/file";

// ─── Helpers ─────────────────────────────────────────────────────

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

// ─── Props ───────────────────────────────────────────────────────

interface UploadFileDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Called after a successful upload */
  onSuccess: () => void;
  /** Optional folder ID to upload into */
  folderId?: string;
  /** Initial file passed from outside drag & drop */
  initialFile?: File | null;
}

type UploadState = "idle" | "uploading" | "success" | "error";

// ─── Component ───────────────────────────────────────────────────

export function UploadFileDialog({
  open,
  onOpenChange,
  onSuccess,
  folderId,
  initialFile,
}: UploadFileDialogProps) {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploadState, setUploadState] = useState<UploadState>("idle");
  const [error, setError] = useState<string | null>(null);
  const [isDragOver, setIsDragOver] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (open) {
      if (initialFile) {
        setSelectedFile(initialFile);
        setUploadState("idle");
        setError(null);
      } else {
        reset();
      }
    }
  }, [open, initialFile]);

  function reset() {
    setSelectedFile(null);
    setUploadState("idle");
    setError(null);
    setIsDragOver(false);
  }

  function handleClose(value: boolean) {
    if (uploadState === "uploading") return; // prevent closing while uploading
    if (!value) reset();
    onOpenChange(value);
  }

  function handleFileSelect(file: File | undefined) {
    if (!file) return;
    setSelectedFile(file);
    setUploadState("idle");
    setError(null);
  }

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
    const file = e.dataTransfer.files[0];
    handleFileSelect(file);
  }, []);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(true);
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
  }, []);

  async function handleUpload() {
    if (!selectedFile) return;
    setUploadState("uploading");
    setError(null);

    try {
      await uploadSingle(selectedFile, folderId);
      setUploadState("success");
      onSuccess();
      // Close after a short delay so user sees "success"
      setTimeout(() => handleClose(false), 600);
    } catch (err) {
      setUploadState("error");
      setError(err instanceof Error ? err.message : "Upload failed");
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Upload File</DialogTitle>
          <DialogDescription>
            Select a file to upload. Drag and drop or click to browse.
          </DialogDescription>
        </DialogHeader>

        {/* Drop zone */}
        <div
          onDrop={handleDrop}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onClick={() => inputRef.current?.click()}
          className={`
            cursor-pointer rounded-lg border-2 border-dashed p-8 text-center
            transition-colors duration-200
            ${isDragOver
              ? "border-primary bg-primary/5"
              : "border-border hover:border-primary/50 hover:bg-muted/30"
            }
          `}
        >
          <input
            ref={inputRef}
            type="file"
            className="hidden"
            onChange={(e) => handleFileSelect(e.target.files?.[0])}
          />

          {selectedFile ? (
            <div className="flex flex-col items-center gap-2">
              <FileIcon className="h-10 w-10 text-primary" />
              <div className="space-y-0.5">
                <p className="text-sm font-medium truncate max-w-[300px]">
                  {selectedFile.name}
                </p>
                <p className="text-xs text-muted-foreground">
                  {formatBytes(selectedFile.size)} · {selectedFile.type || "Unknown type"}
                </p>
              </div>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="mt-1 h-7 gap-1 text-xs text-muted-foreground"
                onClick={(e) => {
                  e.stopPropagation();
                  setSelectedFile(null);
                  setUploadState("idle");
                  setError(null);
                }}
              >
                <X className="h-3 w-3" />
                Remove
              </Button>
            </div>
          ) : (
            <div className="flex flex-col items-center gap-2">
              <CloudUpload className="h-10 w-10 text-muted-foreground/50" />
              <div className="space-y-0.5">
                <p className="text-sm font-medium text-muted-foreground">
                  Drop file here or click to browse
                </p>
                <p className="text-xs text-muted-foreground/70">
                  Any file type supported
                </p>
              </div>
            </div>
          )}
        </div>

        {/* Upload progress / status */}
        {uploadState === "uploading" && (
          <div className="space-y-2">
            <Progress value={undefined} className="h-2" />
            <p className="text-xs text-muted-foreground text-center">Uploading...</p>
          </div>
        )}

        {uploadState === "success" && (
          <p className="text-sm text-green-600 text-center font-medium">
            ✓ Upload successful
          </p>
        )}

        {uploadState === "error" && error && (
          <p className="text-sm text-destructive text-center">{error}</p>
        )}

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => handleClose(false)}
            disabled={uploadState === "uploading"}
          >
            Cancel
          </Button>
          <Button
            onClick={handleUpload}
            disabled={!selectedFile || uploadState === "uploading" || uploadState === "success"}
          >
            {uploadState === "uploading" && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            {uploadState === "uploading" ? "Uploading..." : "Upload"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
