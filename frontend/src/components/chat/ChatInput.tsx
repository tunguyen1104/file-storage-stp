"use client";

import React, { useState, useRef, useEffect } from "react";
import { Send, Loader2 } from "lucide-react";
import { useChat } from "@/contexts/chat-context";
import { useRagMessages } from "@/hooks/use-rag-messages";
import { Button } from "@/components/ui/button";

export function ChatInput() {
  const { activeSessionId } = useChat();
  const { sendMessage } = useRagMessages(activeSessionId, 1, 50);
  
  const [content, setContent] = useState("");
  const [isSending, setIsSending] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Auto-resize textarea
  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = "auto";
      textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 200)}px`;
    }
  }, [content]);

  const handleSend = async () => {
    const trimmed = content.trim();
    if (!trimmed || !activeSessionId || isSending) return;

    try {
      setIsSending(true);
      setContent(""); // Clear input early for better perceived performance
      
      // Reset height
      if (textareaRef.current) {
        textareaRef.current.style.height = "auto";
      }

      await sendMessage(trimmed);
    } catch (error) {
      // Restore content on error
      setContent(trimmed);
      console.error("Failed to send message:", error);
    } finally {
      setIsSending(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  if (!activeSessionId) return null;

  return (
    <div className="border-t border-gray-200 bg-white p-4">
      <div className="mx-auto flex max-w-3xl items-end gap-2 rounded-2xl border border-gray-300 bg-white p-2 shadow-sm focus-within:border-blue-500 focus-within:ring-1 focus-within:ring-blue-500 transition-all">
        <textarea
          ref={textareaRef}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Hỏi về tài liệu của bạn..."
          className="max-h-[200px] min-h-[44px] w-full resize-none border-0 bg-transparent py-3 pl-3 pr-2 text-[15px] outline-none placeholder:text-gray-400 disabled:opacity-50"
          disabled={isSending}
          rows={1}
        />
        <Button
          onClick={handleSend}
          disabled={!content.trim() || isSending}
          size="icon"
          className="h-10 w-10 shrink-0 rounded-xl bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50 transition-colors"
        >
          {isSending ? (
            <Loader2 className="h-5 w-5 animate-spin" />
          ) : (
            <Send className="h-5 w-5" />
          )}
        </Button>
      </div>
      <div className="mx-auto mt-2 max-w-3xl text-center text-xs text-gray-400">
        AI có thể mắc lỗi. Vui lòng kiểm tra lại các thông tin quan trọng.
      </div>
    </div>
  );
}
