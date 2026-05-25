"use client";

import React, { useEffect } from "react";
import { Sheet, SheetContent, SheetTitle, SheetDescription } from "@/components/ui/sheet";
import { useChat } from "@/contexts/chat-context";
import { ChatSidebar } from "./ChatSidebar";
import { ChatMessageList } from "./ChatMessageList";
import { ChatInput } from "./ChatInput";
import { useRagSessions } from "@/hooks/use-rag-sessions";
import { Loader2, Bot, PanelLeftOpen } from "lucide-react";
import { Button } from "@/components/ui/button";

export function ChatSheet() {
  const { isOpen, setIsOpen, activeSessionId, setActiveSessionId } = useChat();
  const { sessions, isLoading, createSession } = useRagSessions(1, 50);
  const [isSidebarCollapsed, setIsSidebarCollapsed] = React.useState(false);

  // Auto-select the first session or create a new one if none exist when opening chat
  useEffect(() => {
    if (isOpen && !isLoading && !activeSessionId) {
      if (sessions.length > 0) {
        setActiveSessionId(sessions[0].id);
      } else {
        createSession().then((newSession) => {
          setActiveSessionId(newSession.id);
        });
      }
    }
  }, [isOpen, isLoading, sessions, activeSessionId, setActiveSessionId, createSession]);

  return (
    <Sheet open={isOpen} onOpenChange={setIsOpen}>
      <SheetContent
        side="right"
        className="flex w-full flex-col p-0 sm:max-w-md md:max-w-2xl lg:max-w-4xl"
      >
        <div className="sr-only">
          <SheetTitle>Trợ lý AI</SheetTitle>
          <SheetDescription>Hỏi đáp dựa trên tài liệu của bạn</SheetDescription>
        </div>
        
        <div className="flex h-full overflow-hidden bg-gray-50/50 backdrop-blur-sm">
          {/* Sidebar */}
          {!isSidebarCollapsed && (
            <div className="hidden w-72 shrink-0 flex-col border-r border-gray-200 bg-white/80 md:flex transition-all">
              <ChatSidebar onCollapse={() => setIsSidebarCollapsed(true)} />
            </div>
          )}

          {/* Main Chat Area */}
          <div className="flex flex-1 flex-col overflow-hidden bg-white/60">
            {/* Header (Always visible on mobile, visible on desktop if collapsed) */}
            <div className={`flex h-14 items-center border-b border-gray-200 bg-white px-4 ${!isSidebarCollapsed ? "md:hidden" : ""}`}>
              {isSidebarCollapsed && (
                <Button variant="ghost" size="icon" onClick={() => setIsSidebarCollapsed(false)} className="hidden md:flex mr-2 text-gray-500 hover:text-gray-900">
                  <PanelLeftOpen className="h-5 w-5" />
                </Button>
              )}
              <Bot className="mr-2 h-5 w-5 text-blue-600" />
              <span className="font-semibold text-gray-900">Trợ lý AI</span>
            </div>

            {/* Chat Content */}
            {isLoading && !activeSessionId ? (
              <div className="flex flex-1 items-center justify-center">
                <Loader2 className="h-8 w-8 animate-spin text-blue-500" />
              </div>
            ) : (
              <>
                <ChatMessageList />
                <ChatInput />
              </>
            )}
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}
