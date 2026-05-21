import {
  File as FileIcon,
  FileArchive,
  FileAudio,
  FileImage,
  FileText,
  FileVideo,
  Folder as FolderIcon,
} from "lucide-react";
import { toast } from "sonner";

import { getPresignedGetUrl } from "@/lib/api/file";
import { cn } from "@/lib/utils";
import type { SearchHitType } from "@/types/search";

/**
 * Render the icon for a search hit. Mirrors the styling used by
 * src/app/(dashboard)/files/page.tsx renderFileIcon — kept in lockstep so
 * results don't look "different" from the file browser.
 */
export function renderHitIcon(
  type: SearchHitType,
  mimeType: string | null,
  className = "h-5 w-5"
) {
  if (type === "FOLDER" || !mimeType) {
    return <FolderIcon className={cn(className, "text-blue-500 fill-blue-100")} />;
  }
  if (mimeType.startsWith("image/"))
    return <FileImage className={cn(className, "text-sky-500")} />;
  if (mimeType.startsWith("video/"))
    return <FileVideo className={cn(className, "text-purple-500")} />;
  if (mimeType.startsWith("audio/"))
    return <FileAudio className={cn(className, "text-orange-500")} />;
  if (
    mimeType.includes("zip") ||
    mimeType.includes("tar") ||
    mimeType.includes("rar")
  )
    return <FileArchive className={cn(className, "text-amber-500")} />;
  if (
    mimeType.includes("pdf") ||
    mimeType.includes("document") ||
    mimeType.startsWith("text/")
  )
    return <FileText className={cn(className, "text-red-500")} />;
  return <FileIcon className={cn(className, "text-gray-400")} />;
}

/**
 * Strip every tag other than <em>…</em> from a backend highlight string.
 *
 * The backend already HTML-escapes everything except its own `<em>` markers
 * (see search-api-specification.md §4.2), so in practice this is defence in
 * depth: we whitelist `<em>` / `</em>` and escape anything else that looks
 * like a tag opener so it renders as literal text.
 */
export function sanitizeHighlight(html: string): string {
  return html.replace(/<\/?([a-zA-Z][a-zA-Z0-9]*)\b[^>]*>/g, (match, tag) => {
    return String(tag).toLowerCase() === "em" ? match : escapeHtml(match);
  });
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

export function folderHitHref(id: string): string {
  return `/files?folderId=${id}`;
}

export async function openFileHit(fileId: string): Promise<void> {
  try {
    const { getUrl } = await getPresignedGetUrl(fileId);
    window.open(getUrl, "_blank", "noopener,noreferrer");
  } catch (err) {
    toast.error(err instanceof Error ? err.message : "Không thể mở tệp tin");
  }
}

/** Friendly Vietnamese label for a MIME type — used by the filter sidebar. */
export function mimeLabel(mime: string): string {
  if (mime === "application/pdf") return "PDF";
  if (mime.includes("spreadsheet") || mime.includes("excel")) return "Excel";
  if (mime.includes("wordprocessingml") || mime.includes("msword"))
    return "Word";
  if (mime.includes("presentation") || mime.includes("powerpoint"))
    return "PowerPoint";
  if (mime.startsWith("image/")) return `Ảnh ${mime.split("/")[1].toUpperCase()}`;
  if (mime.startsWith("video/")) return `Video ${mime.split("/")[1].toUpperCase()}`;
  if (mime.startsWith("audio/")) return `Âm thanh ${mime.split("/")[1].toUpperCase()}`;
  if (mime === "text/plain") return "Văn bản (TXT)";
  if (mime === "text/csv") return "CSV";
  if (mime.includes("zip") || mime.includes("rar") || mime.includes("tar"))
    return "Lưu trữ";
  return mime;
}
