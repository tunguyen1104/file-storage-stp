"use client";

import { useState, useEffect } from "react";
import useSWR from "swr";
import { Folder, ChevronRight, ChevronDown, Loader2, Home } from "lucide-react";
import { toast } from "sonner";

import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { listFolderItems, moveFolder } from "@/lib/api/folder";
import { moveFile } from "@/lib/api/file";
import { cn } from "@/lib/utils";

export interface MoveItemDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  item: {
    id: string;
    name: string;
    type: "FILE" | "FOLDER";
    parentId: string | null; // null if in root
  } | null;
  onSuccess: () => void;
}

function FolderTreeNode({
  folderId,
  name,
  level = 0,
  item,
  selectedDestId,
  onSelect,
  expandedIds,
  onToggleExpand,
}: {
  folderId: string | "root";
  name: string;
  level?: number;
  item: MoveItemDialogProps["item"];
  selectedDestId: string | null;
  onSelect: (id: string) => void;
  expandedIds: Set<string>;
  onToggleExpand: (id: string) => void;
}) {
  const isExpanded = expandedIds.has(folderId);
  const isRoot = folderId === "root";

  // Only fetch if it's expanded
  const { data: folderData, isLoading } = useSWR(
    isExpanded ? ["move-dialog-folders", folderId] : null,
    ([, fId]) => listFolderItems(fId === "root" ? null : fId, { type: "FOLDER", size: 500 }),
    { revalidateOnFocus: false }
  );

  const folders = folderData?.data ?? [];

  // Disable logic
  // We can't move item into itself (it would create a circular reference).
  const isSelf = item?.type === "FOLDER" && item.id === folderId;
  const isSelected = selectedDestId === folderId;

  return (
    <div className="select-none">
      <div
        className={cn(
          "flex items-center px-2 py-1.5 rounded-lg group cursor-pointer transition-colors",
          isSelected ? "bg-[#7C3AED]/10 text-[#7C3AED]" : "text-gray-700 hover:bg-gray-50",
          isSelf && "opacity-50 cursor-not-allowed"
        )}
        style={{ paddingLeft: `${level * 16 + 8}px` }}
        onClick={() => {
          if (!isSelf) onSelect(folderId);
        }}
      >
        <button
          className={cn(
            "p-1 rounded-sm mr-1 hover:bg-gray-200 transition-colors shrink-0",
            isSelf && "invisible"
          )}
          onClick={(e) => {
            e.stopPropagation();
            if (!isSelf) onToggleExpand(folderId);
          }}
        >
          {isExpanded ? (
            <ChevronDown className="h-4 w-4 text-gray-500" />
          ) : (
            <ChevronRight className="h-4 w-4 text-gray-500" />
          )}
        </button>
        {isRoot ? (
          <Home className="h-4 w-4 mr-2 text-gray-500 shrink-0" />
        ) : (
          <Folder
            className={cn(
              "h-4 w-4 mr-2 shrink-0",
              isSelected ? "text-[#7C3AED] fill-[#7C3AED]/20" : "text-blue-400 fill-blue-100"
            )}
          />
        )}
        <span className={cn("text-sm truncate", isSelected ? "font-semibold" : "font-medium")}>
          {name}
        </span>
      </div>

      {isExpanded && !isSelf && (
        <div className="flex flex-col">
          {isLoading && (
            <div
              className="flex items-center py-2"
              style={{ paddingLeft: `${(level + 1) * 16 + 32}px` }}
            >
              <Loader2 className="h-3 w-3 animate-spin text-gray-400 mr-2" />
              <span className="text-xs text-gray-400">Loading...</span>
            </div>
          )}
          {!isLoading &&
            folders.map((f) => (
              <FolderTreeNode
                key={f.id}
                folderId={f.id}
                name={f.name}
                level={level + 1}
                item={item}
                selectedDestId={selectedDestId}
                onSelect={onSelect}
                expandedIds={expandedIds}
                onToggleExpand={onToggleExpand}
              />
            ))}
          {!isLoading && folders.length === 0 && !isRoot && (
            <div
              className="py-1.5 text-xs text-gray-400 italic"
              style={{ paddingLeft: `${(level + 1) * 16 + 32}px` }}
            >
              Empty folder
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export function MoveItemDialog({
  open,
  onOpenChange,
  item,
  onSuccess,
}: MoveItemDialogProps) {
  // Selection state (which folder is actively selected as the destination)
  const [selectedDestId, setSelectedDestId] = useState<string | null>(null);

  // Tree expansion state
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set(["root"]));

  const [isMoving, setIsMoving] = useState(false);

  // Reset state whenever the dialog opens/closes or item changes
  useEffect(() => {
    if (open) {
      setSelectedDestId(null);
      setExpandedIds(new Set(["root"]));
    }
  }, [open, item]);

  function toggleExpand(id: string) {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  async function handleMove() {
    if (!item || !selectedDestId) return;
    setIsMoving(true);
    try {
      const destId = selectedDestId === "root" ? null : selectedDestId;

      if (item.type === "FOLDER") {
        await moveFolder(item.id, destId);
        toast.success(`Folder "${item.name}" moved successfully`);
      } else {
        await moveFile(item.id, destId);
        toast.success(`File "${item.name}" moved successfully`);
      }
      onSuccess();
      onOpenChange(false);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to move item");
    } finally {
      setIsMoving(false);
    }
  }

  const destId = selectedDestId === "root" ? null : selectedDestId;
  const isMoveDisabled =
    isMoving ||
    !selectedDestId ||
    destId === item?.parentId ||
    (item?.type === "FOLDER" && destId === item.id);

  return (
    <Dialog
      open={open}
      onOpenChange={(val) => {
        if (!isMoving) onOpenChange(val);
      }}
    >
      <DialogContent className="max-w-md p-0 overflow-hidden gap-0 bg-white">
        <DialogHeader className="px-5 py-4 border-b border-gray-100">
          <DialogTitle className="text-lg font-semibold text-gray-900">
            Move {item?.type === "FILE" ? "file" : "folder"}
          </DialogTitle>
          <p className="text-sm text-gray-500 mt-1 truncate">
            Moving <span className="font-medium text-gray-700">{item?.name}</span>
          </p>
        </DialogHeader>

        {/* Tree List */}
        <div className="overflow-y-auto max-h-[400px] min-h-[300px] p-2 hide-scrollbar">
          <FolderTreeNode
            folderId="root"
            name="Root"
            level={0}
            item={item}
            selectedDestId={selectedDestId}
            onSelect={setSelectedDestId}
            expandedIds={expandedIds}
            onToggleExpand={toggleExpand}
          />
        </div>

        {/* Footer */}
        <DialogFooter className="px-5 py-4 border-t border-gray-100 bg-gray-50 flex items-center justify-between sm:justify-between">
          <p className="text-xs text-gray-500">
            {!selectedDestId
              ? "Select a destination"
              : destId === item?.parentId
                ? "Already in this folder"
                : "Ready to move"}
          </p>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isMoving}
              className="shadow-sm"
            >
              Cancel
            </Button>
            <Button
              onClick={handleMove}
              disabled={isMoveDisabled}
              className="bg-[#7C3AED] hover:bg-[#6D28D9] text-white shadow-sm gap-2 min-w-[90px]"
            >
              {isMoving && <Loader2 className="h-4 w-4 animate-spin" />}
              Move
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
