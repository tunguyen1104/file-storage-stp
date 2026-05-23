"use client";

import { useEffect, useRef, useState } from "react";
import { Mail, Search, X } from "lucide-react";

import { Input } from "@/components/ui/input";
import { useDebounce } from "@/hooks/use-debounce";
import { useUserSearch } from "@/hooks/use-audit-logs";
import { cn } from "@/lib/utils";

type Props = {
  value: string;
  onChange: (email: string) => void;
  tenantId?: string;
};

export function UserEmailAutocomplete({ value, onChange, tenantId }: Props) {
  const [input, setInput] = useState(value);
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement | null>(null);

  // Keep local state in sync if the parent clears the filter.
  useEffect(() => {
    setInput(value);
  }, [value]);

  const debounced = useDebounce(input, 350);
  const { data, isLoading } = useUserSearch(debounced, tenantId);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (
        containerRef.current &&
        !containerRef.current.contains(e.target as Node)
      ) {
        setIsOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const trimmed = debounced.trim();
  const enabled = trimmed.length >= 2;
  const results = data ?? [];
  const showHint = enabled && results.length === 4;

  function commit(email: string) {
    onChange(email);
    setInput(email);
    setIsOpen(false);
  }

  return (
    <div ref={containerRef} className="relative">
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
        <Input
          value={input}
          onChange={(e) => {
            setInput(e.target.value);
            setIsOpen(true);
          }}
          onFocus={() => setIsOpen(true)}
          onBlur={() => {
            // Commit on blur if the input differs from the parent value.
            if (input !== value) onChange(input);
          }}
          placeholder="Nhập email người thực hiện..."
          className="h-10 pl-9 pr-9"
          maxLength={255}
          autoComplete="off"
        />
        {input && (
          <button
            type="button"
            onClick={() => {
              setInput("");
              onChange("");
              setIsOpen(false);
            }}
            className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600"
            aria-label="Xoá email"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        )}
      </div>

      {isOpen && enabled && (
        <div className="absolute z-30 mt-1 w-full overflow-hidden rounded-lg border border-gray-200 bg-white shadow-lg">
          {isLoading ? (
            <div className="px-3 py-3 text-sm text-gray-500">Đang tìm...</div>
          ) : results.length === 0 ? (
            <div className="px-3 py-3 text-sm text-gray-500">
              Không có kết quả phù hợp.
            </div>
          ) : (
            <ul className="max-h-64 overflow-y-auto py-1">
              {results.map((r) => (
                <li key={r.id}>
                  <button
                    type="button"
                    onMouseDown={(e) => {
                      // mousedown fires before blur, prevents blur from committing the raw input
                      e.preventDefault();
                      commit(r.email);
                    }}
                    className={cn(
                      "flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-violet-50",
                      r.email === value &&
                        "bg-violet-50 font-medium text-violet-700"
                    )}
                  >
                    <Mail className="h-3.5 w-3.5 text-gray-400" />
                    <span className="truncate">{r.email}</span>
                  </button>
                </li>
              ))}
              {showHint && (
                <li className="border-t border-gray-100 px-3 py-1.5 text-[11px] text-gray-500">
                  Đang hiển thị 4 kết quả đầu — nhập thêm để thu hẹp.
                </li>
              )}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
