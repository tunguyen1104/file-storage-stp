"use client";

import React, { useEffect, useLayoutEffect, useRef, useCallback } from "react";
import { useChat } from "@/contexts/chat-context";
import { ChatMessageItem } from "./ChatMessageItem";
import { Loader2, Bot } from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";

export function ChatMessageList() {
  const {
    activeSessionId,
    messages,
    isLoadingMessages,
    isLoadingOlder,
    hasOlderMessages,
    isSendingMessage,
    loadOlderMessages,
  } = useChat();

  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const topSentinelRef = useRef<HTMLDivElement>(null);

  // 'preserve': triggered before loadOlderMessages, restore after prepend
  // null: default, scroll to bottom on new messages if user is near bottom
  const scrollBehaviorRef = useRef<"preserve" | null>(null);
  const savedScrollHeightRef = useRef(0);
  const wasNearBottomRef = useRef(true);

  // Reset scroll state when session changes
  useEffect(() => {
    wasNearBottomRef.current = true;
    scrollBehaviorRef.current = null;
  }, [activeSessionId]);

  // Fix #1: Use useLayoutEffect + direct scrollTop for reliable scroll in any container
  useLayoutEffect(() => {
    const container = scrollContainerRef.current;
    if (!container) return;

    if (scrollBehaviorRef.current === "preserve") {
      // Fix #6: Preserve scroll position after prepending older messages
      container.scrollTop = container.scrollHeight - savedScrollHeightRef.current;
      scrollBehaviorRef.current = null;
      return;
    }

    // Scroll to bottom on initial load or when user is near bottom
    if (wasNearBottomRef.current) {
      container.scrollTop = container.scrollHeight;
    }
  }, [messages]);

  // Track if user is near bottom
  const handleScroll = useCallback(() => {
    const container = scrollContainerRef.current;
    if (!container) return;
    wasNearBottomRef.current =
      container.scrollHeight - container.scrollTop - container.clientHeight < 150;
  }, []);

  // Fix #6: IntersectionObserver to trigger loading older messages on scroll to top
  const handleLoadOlder = useCallback(() => {
    const container = scrollContainerRef.current;
    if (!container || !hasOlderMessages || isLoadingOlder) return;

    // Save scroll height before prepend so we can restore position
    savedScrollHeightRef.current = container.scrollHeight;
    scrollBehaviorRef.current = "preserve";
    loadOlderMessages();
  }, [hasOlderMessages, isLoadingOlder, loadOlderMessages]);

  useEffect(() => {
    const sentinel = topSentinelRef.current;
    const container = scrollContainerRef.current;
    if (!sentinel || !container) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          handleLoadOlder();
        }
      },
      { root: container, threshold: 0.1 }
    );

    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [handleLoadOlder]);

  if (!activeSessionId) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center p-8 text-center text-gray-500">
        <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-blue-50 text-blue-600">
          <Bot className="h-8 w-8" />
        </div>
        <h3 className="mb-2 text-lg font-medium text-gray-900">AetherFS AI Assistant</h3>
        <p className="max-w-md text-sm">
          Chọn một đoạn chat hoặc tạo đoạn chat mới để bắt đầu. AI có thể trả lời các câu hỏi dựa
          trên nội dung các tệp tài liệu bạn đã tải lên.
        </p>
      </div>
    );
  }

  if (isLoadingMessages) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-blue-500" />
      </div>
    );
  }

  return (
    <div
      ref={scrollContainerRef}
      onScroll={handleScroll}
      className="flex-1 overflow-y-auto p-4 md:p-6"
    >
      <div className="mx-auto flex max-w-3xl flex-col gap-6 pb-4">
        {/* Sentinel at the top triggers loading older messages */}
        <div ref={topSentinelRef} className="h-1" />

        {isLoadingOlder && (
          <div className="flex justify-center py-2">
            <Loader2 className="h-5 w-5 animate-spin text-gray-400" />
          </div>
        )}

        {messages.length === 0 && !isSendingMessage ? (
          <div className="flex flex-col items-center justify-center py-20 text-gray-500">
            <Bot className="mb-4 h-12 w-12 text-gray-300" />
            <p>Hãy đặt câu hỏi về tài liệu của bạn.</p>
          </div>
        ) : (
          <AnimatePresence initial={false}>
            {messages.map((message) => (
              <motion.div
                key={message.id}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.3 }}
              >
                <ChatMessageItem message={message} />
              </motion.div>
            ))}

            {/* Fix #2: Typing indicator while waiting for AI response */}
            {isSendingMessage && (
              <motion.div
                key="typing-indicator"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.2 }}
              >
                <ChatMessageItem isTyping />
              </motion.div>
            )}
          </AnimatePresence>
        )}
      </div>
    </div>
  );
}
