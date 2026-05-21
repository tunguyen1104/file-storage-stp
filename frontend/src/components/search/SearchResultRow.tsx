"use client";

import Link from "next/link";
import { ChevronRight, FolderOpen } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import {
  folderHitHref,
  mimeLabel,
  openFileHit,
  renderHitIcon,
  sanitizeHighlight,
} from "@/components/search/search-helpers";
import type { SearchHitItem } from "@/types/search";

const DATE_FMT = new Intl.DateTimeFormat("vi-VN", {
  dateStyle: "medium",
  timeStyle: "short",
});

function pickTitleHtml(hit: SearchHitItem): string {
  if (hit.type === "FILE" && hit.highlights.originalName?.[0]) {
    return sanitizeHighlight(hit.highlights.originalName[0]);
  }
  if (hit.type === "FOLDER" && hit.highlights.name?.[0]) {
    return sanitizeHighlight(hit.highlights.name[0]);
  }
  // No highlight for the title — escape the plain name.
  return escapeText(hit.name);
}

function escapeText(s: string): string {
  return s
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

export function SearchResultRow({ hit }: { hit: SearchHitItem }) {
  const titleHtml = pickTitleHtml(hit);
  const contentSnippets = hit.type === "FILE" ? hit.highlights.content : undefined;

  // Shared visual content — wrapped by either a Link (folders) or a
  // button (files, which open via a presigned URL fetched on click).
  const inner = (
    <>
      <span className="mt-0.5 shrink-0">
        {renderHitIcon(hit.type, hit.mimeType, "h-6 w-6")}
      </span>

      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <h3
            className="truncate text-sm font-semibold text-gray-800 group-hover:text-[#7C3AED]"
            // Backend HTML-escapes the snippet and only ever inserts <em>;
            // sanitizeHighlight enforces the same whitelist defensively.
            dangerouslySetInnerHTML={{ __html: titleHtml }}
          />
          {hit.type === "FOLDER" ? (
            <Badge variant="secondary" className="text-[10px]">
              Thư mục
            </Badge>
          ) : hit.mimeType ? (
            <Badge variant="secondary" className="text-[10px]">
              {mimeLabel(hit.mimeType)}
            </Badge>
          ) : null}
        </div>

        {hit.folderPath ? (
          <div className="mt-1 flex items-center gap-1 text-xs text-gray-400">
            <FolderOpen className="h-3 w-3 shrink-0" />
            <span className="truncate">{hit.folderPath}</span>
          </div>
        ) : null}

        {contentSnippets?.length ? (
          <ul className="mt-2 space-y-1">
            {contentSnippets.map((raw, i) => (
              <li
                key={i}
                className="text-xs leading-relaxed text-gray-600 [&_em]:bg-yellow-100 [&_em]:font-medium [&_em]:not-italic [&_em]:text-gray-900"
                dangerouslySetInnerHTML={{
                  __html: sanitizeHighlight(raw),
                }}
              />
            ))}
          </ul>
        ) : null}

        <div className="mt-2 flex items-center gap-1 text-xs text-gray-400">
          <span>{DATE_FMT.format(new Date(hit.createdAt))}</span>
          <ChevronRight className="ml-auto h-4 w-4 opacity-0 transition-opacity group-hover:opacity-100" />
        </div>
      </div>
    </>
  );

  const containerClass =
    "group flex w-full items-start gap-3 rounded-xl border border-gray-100 bg-white px-4 py-3 text-left transition-all hover:border-gray-200 hover:bg-gray-50 hover:shadow-sm";

  if (hit.type === "FOLDER") {
    return (
      <Link href={folderHitHref(hit.id)} className={containerClass}>
        {inner}
      </Link>
    );
  }

  return (
    <button
      type="button"
      onClick={() => void openFileHit(hit.id)}
      className={containerClass}
    >
      {inner}
    </button>
  );
}
