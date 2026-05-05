"use client";

import { useCallback, useRef, useState } from "react";
import { CloudUpload, AlertCircle } from "lucide-react";
import { Card } from "@/components/ui/card";
import { ALLOWED_MIME_LABEL, MAX_FILE_SIZE } from "@/lib/constants";
import { formatBytes } from "@/lib/utils/upload-utils";
import { cn } from "@/lib/utils";

interface UploadDropZoneProps {
  onFilesSelected: (files: File[]) => void;
  folderId?: string;
}

export function UploadDropZone({ onFilesSelected }: UploadDropZoneProps) {
  const [isDragging, setIsDragging] = useState(false);
  const [oversizeFiles, setOversizeFiles] = useState<string[]>([]);
  const inputRef = useRef<HTMLInputElement>(null);

  const handleFiles = useCallback(
    (files: FileList | null) => {
      if (!files || files.length === 0) return;
      const arr = Array.from(files);

      // Inline size validation: collect oversize names for display; valid files go to handler
      const tooBig = arr.filter((f) => f.size > MAX_FILE_SIZE).map((f) => f.name);
      const valid = arr.filter((f) => f.size <= MAX_FILE_SIZE);

      setOversizeFiles(tooBig);
      if (valid.length > 0) {
        onFilesSelected(valid);
      }
    },
    [onFilesSelected]
  );

  const handleDrop = useCallback(
    (e: React.DragEvent<HTMLDivElement>) => {
      e.preventDefault();
      setIsDragging(false);
      handleFiles(e.dataTransfer.files);
    },
    [handleFiles]
  );

  const handleDragOver = useCallback((e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(true);
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
  }, []);

  const handleClick = useCallback(() => {
    inputRef.current?.click();
  }, []);

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      handleFiles(e.target.files);
      // Reset input so same file can be re-selected
      e.target.value = "";
    },
    [handleFiles]
  );

  return (
    <div className="flex flex-col gap-3">
      <Card
        className={cn(
          "cursor-pointer border-2 border-dashed transition-all duration-200 select-none",
          isDragging
            ? "border-primary bg-primary/5 shadow-md"
            : "border-border hover:border-primary/50 hover:bg-muted/20"
        )}
        onClick={handleClick}
        onDrop={handleDrop}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
      >
        <div className="flex flex-col items-center justify-center gap-4 py-12 px-6 text-center">
          <div
            className={cn(
              "flex h-16 w-16 items-center justify-center rounded-full transition-colors",
              isDragging ? "bg-primary/10" : "bg-muted"
            )}
          >
            <CloudUpload
              className={cn(
                "h-8 w-8 transition-colors",
                isDragging ? "text-primary" : "text-muted-foreground"
              )}
            />
          </div>

          <div className="space-y-1">
            <p className="text-base font-semibold text-foreground">
              {isDragging ? "Drop files here" : "Click or drag files to upload"}
            </p>
            <p className="text-sm text-muted-foreground">
              Supports multiple files at once
            </p>
            <p className="text-xs text-muted-foreground/70 mt-2">{ALLOWED_MIME_LABEL}</p>
            <p className="text-xs text-muted-foreground/70">
              Max size: {formatBytes(MAX_FILE_SIZE)} per file
            </p>
          </div>
        </div>

        <input
          ref={inputRef}
          type="file"
          multiple
          accept="*/*"
          className="hidden"
          onChange={handleInputChange}
        />
      </Card>

      {/* Inline oversize error badges */}
      {oversizeFiles.length > 0 && (
        <div className="flex flex-col gap-1.5">
          {oversizeFiles.map((name) => (
            <div
              key={name}
              className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive"
            >
              <AlertCircle className="h-4 w-4 shrink-0" />
              <span className="truncate">
                <span className="font-medium">{name}</span> exceeds the{" "}
                {formatBytes(MAX_FILE_SIZE)} limit — skipped.
              </span>
              <button
                className="ml-auto shrink-0 text-destructive/60 hover:text-destructive"
                onClick={(e) => {
                  e.stopPropagation();
                  setOversizeFiles((prev) => prev.filter((n) => n !== name));
                }}
              >
                ×
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
