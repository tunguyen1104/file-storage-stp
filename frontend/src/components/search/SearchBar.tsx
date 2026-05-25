"use client";

import { useEffect, useId, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Loader2, Search } from "lucide-react";

import { Input } from "@/components/ui/input";
import { useDebounce } from "@/hooks/use-debounce";
import { useSuggest } from "@/hooks/use-suggest";
import { useSearchInput } from "@/contexts/search-input-context";
import { cn } from "@/lib/utils";
import {
  folderHitHref,
  openFileHit,
  renderHitIcon,
} from "@/components/search/search-helpers";

const MAX_DROPDOWN_ITEMS = 8;
const SEARCH_PATH = "/search";

/** Build a /search URL with the given `q` while preserving non-paging filters. */
function buildSearchUrl(q: string, base: URLSearchParams): string {
  const next = new URLSearchParams(base.toString());
  if (q) next.set("q", q);
  else next.delete("q");
  // New query → page resets to 1; other filters carry over.
  next.delete("page");
  const qs = next.toString();
  return qs ? `${SEARCH_PATH}?${qs}` : SEARCH_PATH;
}

type SearchBarProps = {
  /** Extra classes applied to the root wrapper. */
  className?: string;
  /** Override the placeholder; defaults to a Vietnamese hint. */
  placeholder?: string;
};

/**
 * Global SAYT (search-as-you-type) search bar.
 *
 * - Debounces 500 ms (matching the spec recommendation).
 * - Cancels stale in-flight requests via AbortController inside useSuggest.
 * - SAYT dropdown shows on every page, including /search.
 * - On pages other than /search: Enter pushes to /search?q=… (full results page).
 * - On /search: the bar reads from the URL's `?q=` param on entry and on
 *   external URL changes (browser back/forward, filter chips). Typing only
 *   updates the local input — the URL (and therefore the full-text API call)
 *   only updates when the user commits via Enter / button / dropdown footer.
 * - Ctrl/Cmd+K focuses the input from anywhere.
 */
export function SearchBar({ className, placeholder }: SearchBarProps) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const isOnSearchPage = pathname === SEARCH_PATH;

  const inputRef = useRef<HTMLInputElement>(null);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const listboxId = useId();

  // Draft input value lives in context so the /search page can observe it
  // (to know whether the displayed results match what the user is typing).
  const { draft: value, setDraft: setValue } = useSearchInput();
  const [isOpen, setIsOpen] = useState(false);
  // Keyboard-driven selection (ArrowUp/Down). Enter commits this when >= 0.
  const [activeIndex, setActiveIndex] = useState(-1);
  // Mouse-hover index — visual only, never drives Enter. Decoupling these
  // prevents the bug where hovering an item and moving the mouse away left
  // activeIndex set, so Enter would open that file instead of /search.
  const [hoverIndex, setHoverIndex] = useState(-1);

  const debounced = useDebounce(value, 500);
  const { items, isLoading } = useSuggest(debounced, {
    size: MAX_DROPDOWN_ITEMS,
  });
  const trimmedDebounced = debounced.trim();
  const shouldShowDropdown = isOpen && trimmedDebounced.length > 0;

  // Reset active highlight whenever the result set changes.
  useEffect(() => {
    setActiveIndex(-1);
    setHoverIndex(-1);
  }, [items]);

  // URL → input sync (only on /search, only when the user isn't mid-typing).
  // The `debounced !== value` guard prevents clobbering pending keystrokes
  // while the user is still typing. The effect runs only when pathname /
  // searchParams change — typing alone never triggers it, so local edits
  // stay local until the user commits via Enter (see `submitSearch`).
  useEffect(() => {
    if (!isOnSearchPage) return;
    const urlQ = searchParams.get("q") ?? "";
    if (urlQ === value) return;
    if (debounced !== value) return;
    setValue(urlQ);
    // Intentional: we want this to re-run when pathname/searchParams change.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOnSearchPage, searchParams]);

  // Close on outside click.
  useEffect(() => {
    if (!shouldShowDropdown) return;
    const handler = (e: MouseEvent) => {
      if (!wrapperRef.current) return;
      if (!wrapperRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [shouldShowDropdown]);

  // Ctrl/Cmd+K focuses the search bar — common UX shortcut.
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const isShortcut =
        (e.key === "k" || e.key === "K") && (e.metaKey || e.ctrlKey);
      if (!isShortcut) return;
      e.preventDefault();
      inputRef.current?.focus();
      inputRef.current?.select();
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, []);

  function selectItem(index: number): void {
    const item = items[index];
    if (!item) return;
    setIsOpen(false);
    if (item.type === "FOLDER") {
      // Navigating away to /files — clear the input so the navbar
      // doesn't show a stale query on the destination page.
      setValue("");
      router.push(folderHitHref(item.id));
      return;
    }
    // FILE: opens in a new tab via a presigned URL. The user stays on
    // the current page, so preserve the query in the input — they can
    // re-open the dropdown without retyping.
    void openFileHit(item.id);
  }

  function submitSearch(): void {
    const q = value.trim();
    if (q.length === 0) return;
    setIsOpen(false);
    // On /search, flush the pending debounce immediately so the user gets
    // results without waiting. Elsewhere, push the full results page.
    if (isOnSearchPage) {
      router.replace(buildSearchUrl(q, searchParams));
    } else {
      router.push(`/search?q=${encodeURIComponent(q)}`);
    }
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>): void {
    switch (e.key) {
      case "ArrowDown":
        if (!shouldShowDropdown || items.length === 0) return;
        e.preventDefault();
        setActiveIndex((prev) => (prev + 1) % items.length);
        break;
      case "ArrowUp":
        if (!shouldShowDropdown || items.length === 0) return;
        e.preventDefault();
        setActiveIndex((prev) =>
          prev <= 0 ? items.length - 1 : prev - 1
        );
        break;
      case "Enter":
        e.preventDefault();
        if (shouldShowDropdown && activeIndex >= 0) {
          selectItem(activeIndex);
        } else {
          submitSearch();
        }
        break;
      case "Escape":
        if (shouldShowDropdown) {
          e.preventDefault();
          setIsOpen(false);
        }
        break;
    }
  }

  return (
    <div
      ref={wrapperRef}
      className={cn("relative w-full", className)}
    >
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
      <Input
        ref={inputRef}
        role="combobox"
        aria-expanded={shouldShowDropdown}
        aria-controls={listboxId}
        aria-autocomplete="list"
        aria-activedescendant={
          shouldShowDropdown && activeIndex >= 0
            ? `${listboxId}-opt-${activeIndex}`
            : undefined
        }
        type="search"
        autoComplete="off"
        spellCheck={false}
        placeholder={placeholder ?? "Tìm tệp tin, thư mục..."}
        className="h-10 border-gray-200 bg-gray-50 pl-9 pr-16 text-sm placeholder:text-gray-400 focus-visible:ring-[#7C3AED]/30"
        value={value}
        onChange={(e) => {
          setValue(e.target.value);
          setIsOpen(true);
        }}
        onFocus={() => {
          if (trimmedDebounced.length > 0) setIsOpen(true);
        }}
        onKeyDown={handleKeyDown}
      />

      {isLoading && shouldShowDropdown ? (
        <Loader2 className="absolute right-12 top-1/2 h-4 w-4 -translate-y-1/2 animate-spin text-gray-400" />
      ) : null}

      {/* Subtle keyboard-shortcut hint, hidden on small screens. */}
      <kbd className="pointer-events-none absolute right-3 top-1/2 hidden -translate-y-1/2 select-none items-center gap-1 rounded border border-gray-200 bg-white px-1.5 py-0.5 text-[10px] font-medium text-gray-400 md:inline-flex">
        Ctrl K
      </kbd>

      {shouldShowDropdown ? (
        <div
          id={listboxId}
          role="listbox"
          className="absolute left-0 right-0 top-full z-50 mt-2 overflow-hidden rounded-lg border border-gray-200 bg-white shadow-xl"
        >
          {items.length === 0 && !isLoading ? (
            <div className="px-4 py-6 text-center">
              <p className="text-sm text-gray-500">Không tìm thấy kết quả khớp nhanh.</p>
              <p className="mt-1 text-xs text-gray-400">Nhấn Enter để tìm kiếm toàn văn bản — có thể tìm được nội dung bên trong file.</p>
            </div>
          ) : (
            <ul className="max-h-80 overflow-y-auto py-1">
              {items.map((item, idx) => {
                const isKeyboardActive = idx === activeIndex;
                const isHovered = idx === hoverIndex;
                const isHighlighted = isKeyboardActive || isHovered;
                return (
                  <li
                    key={item.id}
                    id={`${listboxId}-opt-${idx}`}
                    role="option"
                    aria-selected={isKeyboardActive}
                    onMouseEnter={() => setHoverIndex(idx)}
                    onMouseLeave={() =>
                      setHoverIndex((prev) => (prev === idx ? -1 : prev))
                    }
                    onMouseDown={(e) => {
                      // mousedown so the click fires before the input blurs.
                      e.preventDefault();
                      selectItem(idx);
                    }}
                    className={cn(
                      "flex cursor-pointer items-center gap-3 px-3 py-2 transition-colors",
                      isHighlighted ? "bg-[#F3E8FF]" : "hover:bg-gray-50"
                    )}
                  >
                    <span className="shrink-0">
                      {renderHitIcon(item.type, item.mimeType, "h-5 w-5")}
                    </span>
                    <div className="flex min-w-0 flex-1 flex-col">
                      <span className="truncate text-sm font-medium text-gray-800">
                        {item.name}
                      </span>
                      {item.folderPath ? (
                        <span className="truncate text-xs text-gray-400">
                          {item.folderPath}
                        </span>
                      ) : null}
                    </div>
                    <span className="shrink-0 text-[10px] font-medium uppercase tracking-wide text-gray-400">
                      {item.type === "FOLDER" ? "Thư mục" : "Tệp"}
                    </span>
                  </li>
                );
              })}

              {value.trim().length > 0 ? (
                <li
                  onMouseDown={(e) => {
                    e.preventDefault();
                    submitSearch();
                  }}
                  className="flex cursor-pointer items-center gap-3 border-t border-gray-100 px-3 py-2 text-sm font-medium text-[#7C3AED] hover:bg-[#F3E8FF]/40"
                >
                  <Search className="h-4 w-4" />
                  <span className="truncate">
                    Xem tất cả kết quả cho “{value.trim()}”
                  </span>
                </li>
              ) : null}
            </ul>
          )}

          {/* Static hint: stays at the bottom of the popup regardless of
              scroll position, in both results and no-results states.
              Tells the user that Enter widens the search to file contents
              (the suggest API only matches names). */}
          <div className="border-t border-gray-100 bg-gray-50 px-3 py-2 text-[11px] text-gray-500">
            Nhấn{" "}
            <kbd className="rounded border border-gray-200 bg-white px-1 py-0.5 text-[10px] font-medium text-gray-600">
              Enter
            </kbd>{" "}
            để tìm trong nội dung tệp tin
          </div>
        </div>
      ) : null}
    </div>
  );
}
