"use client";

import React, { useState } from "react";
import { Plus, MessageSquare, MoreVertical, Pencil, Trash2, Loader2, Bot, PanelLeftClose } from "lucide-react";
import { format } from "date-fns";
import { useChat } from "@/contexts/chat-context";
import { useRagSessions } from "@/hooks/use-rag-sessions";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

interface ChatSidebarProps {
  onCollapse?: () => void;
}

export function ChatSidebar({ onCollapse }: ChatSidebarProps) {
  const { activeSessionId, setActiveSessionId } = useChat();
  const { sessions, isLoading, createSession, renameSession, deleteSession } = useRagSessions(1, 50);

  const [isCreating, setIsCreating] = useState(false);
  
  // Rename state
  const [sessionToRename, setSessionToRename] = useState<{id: string, title: string} | null>(null);
  const [newTitle, setNewTitle] = useState("");
  const [isRenaming, setIsRenaming] = useState(false);

  // Delete state
  const [sessionToDelete, setSessionToDelete] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const handleCreateNew = async () => {
    try {
      setIsCreating(true);
      const newSession = await createSession();
      setActiveSessionId(newSession.id);
    } finally {
      setIsCreating(false);
    }
  };

  const handleRename = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!sessionToRename || !newTitle.trim()) return;
    
    try {
      setIsRenaming(true);
      await renameSession(sessionToRename.id, newTitle.trim());
      setSessionToRename(null);
    } finally {
      setIsRenaming(false);
    }
  };

  const handleDelete = async () => {
    if (!sessionToDelete) return;
    
    try {
      setIsDeleting(true);
      await deleteSession(sessionToDelete);
      if (activeSessionId === sessionToDelete) {
        setActiveSessionId(null);
      }
      setSessionToDelete(null);
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <div className="flex h-full flex-col">
      <div className="flex flex-col gap-4 p-4 pb-2">
        <div className="flex items-center justify-between px-2">
          <div className="flex items-center gap-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-blue-500 to-indigo-600 text-white shadow-sm">
              <Bot className="h-5 w-5" />
            </div>
            <span className="font-semibold text-gray-900">Trợ lý AI</span>
          </div>
          {onCollapse && (
            <Button variant="ghost" size="icon" onClick={onCollapse} className="text-gray-500 hover:text-gray-900 hidden md:flex">
              <PanelLeftClose className="h-5 w-5" />
            </Button>
          )}
        </div>
        
        <Button
          onClick={handleCreateNew}
          disabled={isCreating}
          className="w-full justify-start gap-2 bg-white text-gray-700 shadow-sm hover:bg-gray-50 border border-gray-200"
        >
          {isCreating ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
          Đoạn chat mới
        </Button>
      </div>

      <ScrollArea className="flex-1 px-3">
        <div className="flex flex-col gap-1 pb-4 pt-2">
          {isLoading ? (
            <div className="flex justify-center p-4">
              <Loader2 className="h-5 w-5 animate-spin text-gray-400" />
            </div>
          ) : sessions.length === 0 ? (
            <div className="px-4 py-8 text-center text-sm text-gray-500">
              Chưa có đoạn chat nào.
            </div>
          ) : (
            sessions.map((session) => (
              <div
                key={session.id}
                className={cn(
                  "group relative flex items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors cursor-pointer",
                  activeSessionId === session.id
                    ? "bg-blue-50 text-blue-700 font-medium"
                    : "text-gray-700 hover:bg-gray-100"
                )}
                onClick={() => setActiveSessionId(session.id)}
              >
                <MessageSquare className={cn(
                  "h-4 w-4 shrink-0 mt-0.5", 
                  activeSessionId === session.id ? "text-blue-600" : "text-gray-400"
                )} />
                <div className="flex flex-1 flex-col overflow-hidden pr-6">
                  <span className="truncate" title={session.title || "Đoạn chat mới"}>{session.title || "Đoạn chat mới"}</span>
                  <span className="text-[10px] text-gray-400">
                    {format(new Date(session.updatedAt), "dd/MM HH:mm")}
                  </span>
                </div>

                <div className={cn(
                  "absolute right-1 flex items-center bg-transparent transition-opacity",
                  activeSessionId === session.id ? "opacity-100" : "opacity-0 group-hover:opacity-100"
                )}>
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild onClick={(e) => e.stopPropagation()}>
                      <Button variant="ghost" size="icon" className="h-7 w-7 rounded-md hover:bg-gray-200">
                        <MoreVertical className="h-4 w-4 text-gray-500" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end" className="w-40">
                      <DropdownMenuItem 
                        onClick={(e) => {
                          e.stopPropagation();
                          setSessionToRename({ id: session.id, title: session.title });
                          setNewTitle(session.title);
                        }}
                      >
                        <Pencil className="mr-2 h-4 w-4" />
                        <span>Đổi tên</span>
                      </DropdownMenuItem>
                      <DropdownMenuItem 
                        className="text-red-600 focus:bg-red-50 focus:text-red-700"
                        onClick={(e) => {
                          e.stopPropagation();
                          setSessionToDelete(session.id);
                        }}
                      >
                        <Trash2 className="mr-2 h-4 w-4" />
                        <span>Xóa</span>
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </div>
              </div>
            ))
          )}
        </div>
      </ScrollArea>

      {/* Rename Dialog */}
      <Dialog open={!!sessionToRename} onOpenChange={(open) => !open && setSessionToRename(null)}>
        <DialogContent className="sm:max-w-md">
          <form onSubmit={handleRename}>
            <DialogHeader>
              <DialogTitle>Đổi tên đoạn chat</DialogTitle>
            </DialogHeader>
            <div className="py-4">
              <Label htmlFor="title" className="sr-only">Tên đoạn chat</Label>
              <Input
                id="title"
                value={newTitle}
                onChange={(e) => setNewTitle(e.target.value)}
                placeholder="Nhập tên đoạn chat..."
                maxLength={255}
                autoFocus
              />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setSessionToRename(null)}>
                Hủy
              </Button>
              <Button type="submit" disabled={isRenaming || !newTitle.trim()}>
                {isRenaming ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
                Lưu
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Dialog */}
      <Dialog open={!!sessionToDelete} onOpenChange={(open) => !open && setSessionToDelete(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Xóa đoạn chat</DialogTitle>
          </DialogHeader>
          <div className="py-4 text-sm text-gray-500">
            Bạn có chắc chắn muốn xóa đoạn chat này không? Hành động này không thể hoàn tác.
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setSessionToDelete(null)}>
              Hủy
            </Button>
            <Button type="button" variant="destructive" onClick={handleDelete} disabled={isDeleting}>
              {isDeleting ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              Xóa
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
