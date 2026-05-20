"use client";

import { useEffect, useState } from "react";
import { Loader2, Download, X, FileIcon, FileText, FileImage, FileVideo, FileAudio, FileArchive, AlertTriangle } from "lucide-react";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { getPresignedGetUrl } from "@/lib/api/file";
import { cn } from "@/lib/utils";

// ─── Constants & Helpers ─────────────────────────────────────────

const PREVIEWABLE_MIME_TYPES = [
  "application/pdf",
  "text/plain",
  "text/csv",
  "text/html",
  "application/json",
  "video/mp4",
  "video/webm",
  "audio/mpeg",
  "audio/ogg",
  "audio/wav",
];

function isPreviewable(mimeType: string | null): boolean {
  if (!mimeType) return false;
  if (mimeType.startsWith("image/")) return true;
  return PREVIEWABLE_MIME_TYPES.includes(mimeType);
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

function getLargeFileIcon(mimeType: string | null) {
  const size = "h-24 w-24";
  if (!mimeType) return <FileIcon className={cn(size, "text-gray-400")} />;
  if (mimeType.startsWith("image/")) return <FileImage className={cn(size, "text-sky-500")} />;
  if (mimeType.startsWith("video/")) return <FileVideo className={cn(size, "text-purple-500")} />;
  if (mimeType.startsWith("audio/")) return <FileAudio className={cn(size, "text-orange-500")} />;
  if (mimeType.includes("zip") || mimeType.includes("tar") || mimeType.includes("rar"))
    return <FileArchive className={cn(size, "text-amber-500")} />;
  if (mimeType === "application/pdf") return <FileText className={cn(size, "text-rose-500")} />;
  if (mimeType.startsWith("text/")) return <FileText className={cn(size, "text-emerald-500")} />;
  return <FileIcon className={cn(size, "text-indigo-500")} />;
}

// ─── Props ───────────────────────────────────────────────────────

interface FilePreviewDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  file: {
    id: string;
    originalName: string;
    mimeType: string | null;
    sizeBytes: number;
    status: string;
  } | null;
}

// ─── Component ───────────────────────────────────────────────────

export function FilePreviewDialog({ open, onOpenChange, file }: FilePreviewDialogProps) {
  const [presignedUrl, setPresignedUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const targetFile = file;
    if (!open || !targetFile) {
      setPresignedUrl(null);
      setError(null);
      return;
    }

    let active = true;
    async function fetchUrl() {
      if (!targetFile) return;
      setLoading(true);
      setError(null);
      try {
        const { getUrl } = await getPresignedGetUrl(targetFile.id);
        if (active) {
          setPresignedUrl(getUrl);
        }
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : "Không thể tải URL xem trước tệp");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    fetchUrl();
    return () => {
      active = false;
    };
  }, [open, file]);

  const handleDownload = () => {
    if (!presignedUrl || !file) return;
    const link = document.createElement("a");
    link.href = presignedUrl;
    link.setAttribute("download", file.originalName);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const isPreviewSupported = file ? isPreviewable(file.mimeType) : false;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent 
        showCloseButton={false} // Hide default Shadcn absolute close button to prevent duplication
        className={cn(
          "p-0 gap-0 overflow-hidden flex flex-col border-none shadow-2xl transition-all duration-300",
          isPreviewSupported 
            ? "w-[95vw] max-w-[95vw] sm:w-[90vw] sm:max-w-[90vw] md:w-[75vw] md:max-w-[75vw] lg:w-[70vw] lg:max-w-[70vw] xl:w-[66vw] xl:max-w-[66vw] h-[92vh] bg-[#0B0F19] text-white rounded-2xl border border-gray-800"
            : "w-full max-w-[480px] sm:max-w-[480px] bg-white text-gray-900 rounded-2xl"
        )}
      >
        {/* Title bar for premium view */}
        <div 
          className={cn(
            "flex items-center justify-between px-6 py-4 shrink-0 border-b",
            isPreviewSupported 
              ? "border-gray-800/80 bg-[#0D1220]/90 backdrop-blur-md"
              : "border-gray-100 bg-gray-50/50"
          )}
        >
          <div className="flex flex-col min-w-0 pr-4">
            <DialogTitle className={cn(
              "text-base font-semibold truncate",
              isPreviewSupported ? "text-gray-100" : "text-gray-900"
            )}>
              {file?.originalName}
            </DialogTitle>
            <span className={cn(
              "text-xs mt-1",
              isPreviewSupported ? "text-gray-400 font-medium" : "text-gray-500"
            )}>
              {file && formatBytes(file.sizeBytes)} • {file?.mimeType || "Không xác định"}
            </span>
          </div>

          <div className="flex items-center gap-2.5 shrink-0">
            {presignedUrl && (
              <Button
                size="sm"
                className={cn(
                  "h-8 px-4 gap-2 font-semibold text-xs rounded-xl transition-all duration-200 hover:scale-[1.02] active:scale-[0.98] cursor-pointer",
                  isPreviewSupported 
                    ? "bg-[#7C3AED] hover:bg-[#6D28D9] text-white hover:shadow-lg hover:shadow-[#7C3AED]/20 border-none" 
                    : "bg-[#7C3AED] hover:bg-[#6D28D9] text-white hover:shadow-lg hover:shadow-[#7C3AED]/20 border-none"
                )}
                onClick={handleDownload}
              >
                <Download className="h-3.5 w-3.5" />
                Tải xuống
              </Button>
            )}
            <button
              onClick={() => onOpenChange(false)}
              className={cn(
                "p-2 rounded-xl transition-all border cursor-pointer focus:outline-none focus:ring-2 focus:ring-offset-2",
                isPreviewSupported
                  ? "bg-gray-800/40 hover:bg-gray-700/60 text-gray-400 hover:text-white border-gray-700/50 focus:ring-gray-700 focus:ring-offset-[#0B0F19]"
                  : "bg-gray-50 hover:bg-gray-100 text-gray-500 hover:text-gray-900 border-gray-200 focus:ring-primary/20"
              )}
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>

        {/* Content Body */}
        <div className="flex-1 min-h-0 relative flex flex-col justify-center items-center overflow-hidden">
          {loading && (
            <div className="absolute inset-0 flex flex-col items-center justify-center bg-transparent backdrop-blur-[2px] z-50">
              <Loader2 className={cn("h-10 w-10 animate-spin mb-3", isPreviewSupported ? "text-[#7C3AED]" : "text-[#7C3AED]")} />
              <p className={cn("text-xs font-semibold", isPreviewSupported ? "text-gray-400 animate-pulse" : "text-gray-500 animate-pulse")}>
                Đang chuẩn bị hiển thị tệp...
              </p>
            </div>
          )}

          {error && (
            <div className="p-8 text-center max-w-sm flex flex-col items-center">
              <div className="h-12 w-12 rounded-full bg-red-50 text-red-500 flex items-center justify-center mb-4">
                <AlertTriangle className="h-6 w-6" />
              </div>
              <h3 className="text-sm font-semibold text-gray-900 mb-1">Không thể tải bản xem trước</h3>
              <p className="text-xs text-gray-500 mb-4">{error}</p>
              <Button 
                size="sm" 
                onClick={() => onOpenChange(false)}
                className="bg-[#7C3AED] hover:bg-[#6D28D9]"
              >
                Đóng
              </Button>
            </div>
          )}

          {!loading && !error && file && (
            <>
              {isPreviewSupported ? (
                // ─── PREVIEW VIEW ─────────────────────────────────
                <div className="w-full h-full bg-[#070913] flex items-center justify-center overflow-hidden p-0 m-0">
                  {presignedUrl ? (
                    file.mimeType?.startsWith("image/") ? (
                      <img 
                        src={presignedUrl} 
                        alt={file.originalName} 
                        className="max-w-full max-h-full object-contain p-6 transition-all duration-300 hover:scale-[1.01]" 
                      />
                    ) : file.mimeType?.startsWith("video/") ? (
                      <video 
                        src={presignedUrl} 
                        controls 
                        autoPlay
                        className="max-w-full max-h-full p-6 focus:outline-none" 
                      />
                    ) : file.mimeType?.startsWith("audio/") ? (
                      <div className="flex flex-col items-center justify-center p-10 bg-[#0F172A] rounded-2xl shadow-2xl border border-gray-800 w-[450px]">
                        <FileAudio className="h-20 w-20 text-[#7C3AED] mb-6 animate-bounce" />
                        <h4 className="text-base font-semibold text-gray-200 mb-6 truncate max-w-full">{file.originalName}</h4>
                        <audio 
                          src={presignedUrl} 
                          controls 
                          autoPlay
                          className="w-full focus:outline-none" 
                        />
                      </div>
                    ) : (
                      <iframe
                        src={presignedUrl}
                        title={file.originalName}
                        className="w-full h-full border-none bg-white p-0 m-0 overflow-hidden"
                      />
                    )
                  ) : null}
                </div>
              ) : (
                // ─── DOWNLOAD INTERFACE (PREVIEW NOT SUPPORTED) ────
                <div className="flex flex-col items-center justify-center p-10 text-center bg-white w-full rounded-b-2xl">
                  <div className="mb-6 p-5 bg-[#7C3AED]/5 rounded-3xl border border-[#7C3AED]/10 shadow-inner">
                    {getLargeFileIcon(file.mimeType)}
                  </div>
                  <h3 className="text-lg font-bold text-gray-900 mb-2 truncate max-w-[380px] px-2" title={file.originalName}>
                    {file.originalName}
                  </h3>
                  <p className="text-xs text-gray-500 mb-8 max-w-[320px] leading-relaxed">
                    Định dạng tệp này không hỗ trợ xem trực tuyến. Vui lòng tải xuống máy để xem nội dung đầy đủ.
                  </p>
                  {presignedUrl ? (
                    <Button 
                      onClick={handleDownload}
                      className="w-full py-3 bg-gradient-to-r from-[#7C3AED] to-[#6D28D9] hover:from-[#6D28D9] hover:to-[#5B21B6] text-white font-semibold text-sm rounded-xl gap-2 shadow-lg shadow-[#7C3AED]/25 transition-all hover:scale-[1.02] active:scale-[0.98] cursor-pointer"
                    >
                      <Download className="h-4 w-4" />
                      Tải xuống ngay
                    </Button>
                  ) : (
                    <Button 
                      disabled
                      className="w-full py-3 bg-gray-100 text-gray-400 font-semibold text-sm rounded-xl gap-2"
                    >
                      <Loader2 className="h-4 w-4 animate-spin" />
                      Đang lấy liên kết tải...
                    </Button>
                  )}
                </div>
              )}
            </>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
