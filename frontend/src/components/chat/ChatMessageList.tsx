"use client";

import React, { useEffect, useRef } from "react";
import { useChat } from "@/contexts/chat-context";
import { useRagMessages } from "@/hooks/use-rag-messages";
import { ChatMessageItem } from "./ChatMessageItem";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Loader2, Bot } from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";

export function ChatMessageList() {
  const { activeSessionId } = useChat();
  const { messages, isLoading } = useRagMessages(activeSessionId, 1, 50);
  
  const scrollRef = useRef<HTMLDivElement>(null);
  const endOfMessagesRef = useRef<HTMLDivElement>(null);

  // Auto scroll to bottom when messages change
  useEffect(() => {
    if (endOfMessagesRef.current) {
      endOfMessagesRef.current.scrollIntoView({ behavior: "smooth" });
    }
  }, [messages]);

  if (!activeSessionId) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center p-8 text-center text-gray-500">
        <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-blue-50 text-blue-600">
          <Bot className="h-8 w-8" />
        </div>
        <h3 className="mb-2 text-lg font-medium text-gray-900">AetherFS AI Assistant</h3>
        <p className="max-w-md text-sm">
          Chọn một đoạn chat hoặc tạo đoạn chat mới để bắt đầu. 
          AI có thể trả lời các câu hỏi dựa trên nội dung các tệp tài liệu bạn đã tải lên.
        </p>
      </div>
    );
  }

  if (isLoading && messages.length === 0) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-blue-500" />
      </div>
    );
  }

  return (
    <ScrollArea className="flex-1 p-4 md:p-6" ref={scrollRef}>
      <div className="mx-auto flex max-w-3xl flex-col gap-6 pb-4">
        {messages.length === 0 ? (
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
          </AnimatePresence>
        )}
        <div ref={endOfMessagesRef} className="h-1" />
      </div>
    </ScrollArea>
  );
}
