"use client";

import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from "react";
import { ragApi } from "@/lib/api/rag";
import type { RagChatMessageResponse } from "@/types/rag";

const PAGE_SIZE = 50;

interface ChatContextValue {
  isOpen: boolean;
  setIsOpen: (isOpen: boolean) => void;
  toggleChat: () => void;

  activeSessionId: string | null;
  setActiveSessionId: (id: string | null) => void;

  messages: RagChatMessageResponse[];
  isLoadingMessages: boolean;
  isLoadingOlder: boolean;
  hasOlderMessages: boolean;
  isSendingMessage: boolean;
  loadOlderMessages: () => Promise<void>;
  sendMessage: (content: string) => Promise<void>;
}

const ChatContext = createContext<ChatContextValue | undefined>(undefined);

export function ChatProvider({ children }: { children: React.ReactNode }) {
  const [isOpen, setIsOpen] = useState(false);
  const [activeSessionId, setActiveSessionIdState] = useState<string | null>(null);

  const [messages, setMessages] = useState<RagChatMessageResponse[]>([]);
  const [isLoadingMessages, setIsLoadingMessages] = useState(false);
  const [isLoadingOlder, setIsLoadingOlder] = useState(false);
  const [hasOlderMessages, setHasOlderMessages] = useState(false);
  const [isSendingMessage, setIsSendingMessage] = useState(false);

  // Tracks the oldest page we've loaded (for infinite scroll going back in time)
  const oldestLoadedPageRef = useRef<number | null>(null);

  const toggleChat = () => setIsOpen((prev) => !prev);

  const setActiveSessionId = useCallback((id: string | null) => {
    setActiveSessionIdState(id);
  }, []);

  // Load messages when active session changes
  useEffect(() => {
    if (!activeSessionId) {
      setMessages([]);
      setHasOlderMessages(false);
      oldestLoadedPageRef.current = null;
      return;
    }

    let cancelled = false;

    const loadInitial = async () => {
      setIsLoadingMessages(true);
      setMessages([]);
      setHasOlderMessages(false);
      oldestLoadedPageRef.current = null;

      try {
        // Fetch page 1 to discover totalPages
        const firstPage = await ragApi.getMessages(activeSessionId, 1, PAGE_SIZE);
        if (cancelled) return;

        const totalPages = firstPage.totalPages;

        if (totalPages <= 1) {
          setMessages(firstPage.data);
          oldestLoadedPageRef.current = 1;
          setHasOlderMessages(false);
        } else {
          // Fetch the last page to show newest messages first
          const lastPage = await ragApi.getMessages(activeSessionId, totalPages, PAGE_SIZE);
          if (cancelled) return;

          setMessages(lastPage.data);
          oldestLoadedPageRef.current = totalPages;
          setHasOlderMessages(totalPages > 1);
        }
      } finally {
        if (!cancelled) setIsLoadingMessages(false);
      }
    };

    loadInitial();
    return () => {
      cancelled = true;
    };
  }, [activeSessionId]);

  const loadOlderMessages = useCallback(async () => {
    const currentOldest = oldestLoadedPageRef.current;
    if (!activeSessionId || currentOldest === null || currentOldest <= 1 || isLoadingOlder) return;

    setIsLoadingOlder(true);
    const targetPage = currentOldest - 1;

    try {
      const data = await ragApi.getMessages(activeSessionId, targetPage, PAGE_SIZE);
      setMessages((prev) => [...data.data, ...prev]);
      oldestLoadedPageRef.current = targetPage;
      setHasOlderMessages(targetPage > 1);
    } finally {
      setIsLoadingOlder(false);
    }
  }, [activeSessionId, isLoadingOlder]);

  const sendMessage = useCallback(
    async (content: string) => {
      if (!activeSessionId || isSendingMessage) return;

      // Optimistic: show user message immediately
      const optimisticId = `optimistic-${Date.now()}`;
      const optimisticUserMsg: RagChatMessageResponse = {
        id: optimisticId,
        role: "USER",
        content,
        model: null,
        retrievalTopK: null,
        inputTokens: null,
        outputTokens: null,
        createdAt: new Date().toISOString(),
        sources: [],
      };

      setMessages((prev) => [...prev, optimisticUserMsg]);
      setIsSendingMessage(true);

      try {
        const response = await ragApi.sendMessage(activeSessionId, content);

        // Replace optimistic message with real server messages
        setMessages((prev) => [
          ...prev.filter((m) => m.id !== optimisticId),
          response.userMessage,
          response.assistantMessage,
        ]);
      } catch (error) {
        setMessages((prev) => prev.filter((m) => m.id !== optimisticId));
        throw error;
      } finally {
        setIsSendingMessage(false);
      }
    },
    [activeSessionId, isSendingMessage]
  );

  return (
    <ChatContext.Provider
      value={{
        isOpen,
        setIsOpen,
        toggleChat,
        activeSessionId,
        setActiveSessionId,
        messages,
        isLoadingMessages,
        isLoadingOlder,
        hasOlderMessages,
        isSendingMessage,
        loadOlderMessages,
        sendMessage,
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
