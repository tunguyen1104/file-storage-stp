"use client";

import { useEffect, useRef } from "react";
import {
  Folder,
  FileText,
  Monitor,
  LayoutGrid,
  Upload,
  Copy,
  Send,
  Pencil,
  Trash2,
  ChevronRight,
  X,
  MoveRight,
} from "lucide-react";
import { cn } from "@/lib/utils";

interface NewFileMenuProps {
  open: boolean;
  onClose: () => void;
  anchorRef?: React.RefObject<HTMLElement | null>;
  onUpload?: () => void;
  onCreateFolder?: () => void;
  /** Called when Edit is clicked; only provided when inside a folder */
  onEditFolder?: () => void;
  /** Called when Move is clicked; only provided when inside a folder */
  onMoveFolder?: () => void;
  /** Called when Remove is clicked; only provided when inside a folder */
  onRemoveFolder?: () => void;
}

type MenuItem = {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  hasArrow?: boolean;
  danger?: boolean;
  onClick?: () => void;
};

export function NewFileMenu({
  open,
  onClose,
  anchorRef,
  onUpload,
  onCreateFolder,
  onEditFolder,
  onMoveFolder,
  onRemoveFolder,
}: NewFileMenuProps) {
  const menuRef = useRef<HTMLDivElement>(null);

  // Close on outside click
  useEffect(() => {
    if (!open) return;
    function handleClick(e: MouseEvent) {
      if (
        menuRef.current &&
        !menuRef.current.contains(e.target as Node) &&
        (!anchorRef?.current || !anchorRef.current.contains(e.target as Node))
      ) {
        onClose();
      }
    }
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [open, onClose, anchorRef]);

  // Close on Escape
  useEffect(() => {
    if (!open) return;
    function handleKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [open, onClose]);

  if (!open) return null;

  const createItems: MenuItem[] = [
    { icon: Folder, label: "Folder", hasArrow: false, onClick: onCreateFolder },
    { icon: FileText, label: "Document" },
    { icon: Monitor, label: "Presentation" },
    { icon: LayoutGrid, label: "Spreadsheet" },
  ];

  const addItems: MenuItem[] = [
    { icon: Upload, label: "Upload", onClick: onUpload },
    { icon: Copy, label: "Transfer a copy" },
    { icon: Send, label: "Send file request" },
  ];

  const modifyItems: MenuItem[] = [
    ...(onMoveFolder ? [{ icon: MoveRight, label: "Move", hasArrow: false, onClick: onMoveFolder }] : []),
    ...(onEditFolder ? [{ icon: Pencil, label: "Edit", hasArrow: false, onClick: onEditFolder }] : []),
    ...(onRemoveFolder ? [{ icon: Trash2, label: "Remove", hasArrow: false, danger: true, onClick: onRemoveFolder }] : []),
  ];

  return (
    <div
      ref={menuRef}
      className={cn(
        "absolute z-50 w-72 rounded-xl shadow-2xl overflow-hidden",
        "bg-[#1a1a2e] text-white",
        "top-full mt-2 right-0"
      )}
      role="menu"
    >
      {/* Header close button */}
      <div className="flex items-center justify-between px-4 pt-3 pb-1">
        <button
          onClick={onClose}
          className="ml-auto flex h-6 w-6 items-center justify-center rounded-full text-gray-400 hover:text-white hover:bg-white/10 transition-colors"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      </div>

      {/* CREATE section */}
      <MenuSection label="CREATE" items={createItems} />

      {/* ADD section */}
      <MenuSection label="ADD" items={addItems} />

      {/* MODIFY section — only shown when inside a folder */}
      {modifyItems.length > 0 && <MenuSection label="MODIFY" items={modifyItems} />}

      <div className="h-2" />
    </div>
  );
}

// ─── Section sub-component ────────────────────────────────────────

function MenuSection({
  label,
  items,
}: {
  label: string;
  items: MenuItem[];
}) {
  return (
    <div className="px-2 py-1">
      <p className="px-3 pb-1.5 text-[10px] font-semibold tracking-widest text-gray-400 uppercase">
        {label}
      </p>
      {items.map((item, idx) => {
        const Icon = item.icon;
        return (
          <button
            key={item.label}
            onClick={item.onClick}
            className={cn(
              "flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors text-left",
              item.danger
                ? "text-red-400 hover:bg-red-600 hover:text-white"
                : "text-gray-200 hover:bg-[#7C3AED] hover:text-white"
            )}
            role="menuitem"
          >
            <Icon className="h-4 w-4 shrink-0" />
            <span className="flex-1">{item.label}</span>
            {item.hasArrow && <ChevronRight className="h-4 w-4 text-gray-400" />}
          </button>
        );
      })}
    </div>
  );
}
