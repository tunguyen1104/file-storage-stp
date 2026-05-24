"use client";

import React, { createContext, useContext, useState } from "react";
import type { RagChatSessionResponse } from "@/types/rag";

interface ChatContextValue {
  isOpen: boolean;
  setIsOpen: (isOpen: boolean) => void;
  toggleChat: () => void;
  activeSessionId: string | null;
  setActiveSessionId: (id: string | null) => void;
}

const ChatContext = createContext<ChatContextValue | undefined>(undefined);

export function ChatProvider({ children }: { children: React.ReactNode }) {
  const [isOpen, setIsOpen] = useState(false);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);

  const toggleChat = () => setIsOpen((prev) => !prev);

  return (
    <ChatContext.Provider
      value={{
        isOpen,
        setIsOpen,
        toggleChat,
        activeSessionId,
        setActiveSessionId,
      }}
    >
      {children}
    </ChatContext.Provider>
  );
}

export function useChat() {
  const context = useContext(ChatContext);
  if (context === undefined) {
    throw new Error("useChat must be used within a ChatProvider");
  }
  return context;
}
