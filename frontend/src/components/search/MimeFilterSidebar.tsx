"use client";

import { Filter } from "lucide-react";

import { Checkbox } from "@/components/ui/checkbox";
import { mimeLabel } from "@/components/search/search-helpers";

import { cn } from "@/lib/utils";

type MimeFilterSidebarProps = {
  counts: Record<string, number>;
  selected: string[];
  onToggle: (mime: string) => void;
  onClear: () => void;
  /** Greys out the whole panel and disables interactions. */
  disabled?: boolean;
};

export function MimeFilterSidebar({
  counts,
  selected,
  onToggle,
  onClear,
  disabled = false,
}: MimeFilterSidebarProps) {
  const entries = Object.entries(counts).sort((a, b) => b[1] - a[1]);
  const selectedSet = new Set(selected);

  if (entries.length === 0 && selected.length === 0) return null;

  return (
    <aside
      className={cn(
        "w-full shrink-0 rounded-xl border border-gray-200 bg-white p-4 lg:w-64",
        disabled && "opacity-70"
      )}
      aria-disabled={disabled}
    >
      <div className="mb-3 flex items-center justify-between">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-gray-700">
          <Filter className="h-4 w-4 text-gray-400" />
          Lọc theo định dạng
        </h2>
        {selected.length > 0 ? (
          <button
            type="button"
            onClick={onClear}
            disabled={disabled}
            className="text-xs font-medium text-[#7C3AED] hover:underline disabled:cursor-not-allowed disabled:text-gray-400 disabled:no-underline"
          >
            Xóa lọc
          </button>
        ) : null}
      </div>

      {entries.length === 0 ? (
        <p className="text-xs text-gray-400">Không có định dạng nào để lọc.</p>
      ) : (
        <ul className="space-y-1">
          {entries.map(([mime, count]) => {
            const id = `mime-${mime}`;
            const isChecked = selectedSet.has(mime);
            return (
              <li key={mime}>
                <label
                  htmlFor={id}
                  className={cn(
                    "flex items-center gap-2 rounded-md px-2 py-1.5 text-xs text-gray-600",
                    disabled
                      ? "cursor-not-allowed"
                      : "cursor-pointer hover:bg-gray-50"
                  )}
                >
                  <Checkbox
                    id={id}
                    checked={isChecked}
                    onCheckedChange={() => onToggle(mime)}
                    disabled={disabled}
                  />
                  <span className="min-w-0 flex-1 truncate" title={mime}>
                    {mimeLabel(mime)}
                  </span>
                  <span className="shrink-0 tabular-nums text-gray-400">
                    {count}
                  </span>
                </label>
              </li>
            );
          })}
        </ul>
      )}
    </aside>
  );
}
