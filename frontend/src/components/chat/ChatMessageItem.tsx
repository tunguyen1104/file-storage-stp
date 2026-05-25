"use client";

import React from "react";
import { User, Bot, FileText } from "lucide-react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { format } from "date-fns";
import type { RagChatMessageResponse } from "@/types/rag";
import { cn } from "@/lib/utils";

interface ChatMessageItemProps {
  message?: RagChatMessageResponse;
  isTyping?: boolean;
}

export function ChatMessageItem({ message, isTyping }: ChatMessageItemProps) {
  // Fix #2: Typing indicator variant
  if (isTyping) {
    return (
      <div className="flex w-full justify-start">
        <div className="flex max-w-[85%] flex-row gap-4">
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 text-white shadow-sm">
            <Bot className="h-5 w-5" />
          </div>
          <div className="rounded-2xl rounded-tl-sm border border-gray-100 bg-white px-5 py-3.5 shadow-sm">
            <div className="flex h-5 items-center gap-1">
              <span className="h-2 w-2 animate-bounce rounded-full bg-gray-400 [animation-delay:0ms]" />
              <span className="h-2 w-2 animate-bounce rounded-full bg-gray-400 [animation-delay:150ms]" />
              <span className="h-2 w-2 animate-bounce rounded-full bg-gray-400 [animation-delay:300ms]" />
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (!message) return null;

  const isUser = message.role === "USER";

  return (
    <div className={cn("flex w-full", isUser ? "justify-end" : "justify-start")}>
      <div className={cn("flex max-w-[85%] gap-4", isUser ? "flex-row-reverse" : "flex-row")}>
        {/* Avatar */}
        <div
          className={cn(
            "flex h-8 w-8 shrink-0 items-center justify-center rounded-full shadow-sm",
            isUser
              ? "bg-gray-100 text-gray-600"
              : "bg-gradient-to-br from-blue-500 to-indigo-600 text-white"
          )}
        >
          {isUser ? <User className="h-5 w-5" /> : <Bot className="h-5 w-5" />}
        </div>

        {/* Message Content */}
        <div className={cn("flex flex-col gap-1", isUser ? "items-end" : "items-start")}>
          <div
            className={cn(
              "rounded-2xl px-5 py-3.5 shadow-sm overflow-hidden",
              isUser
                ? "bg-gradient-to-r from-blue-600 to-blue-500 text-white rounded-tr-sm"
                : "bg-white border border-gray-100 text-gray-800 rounded-tl-sm"
            )}
          >
            {isUser ? (
              <div className="whitespace-pre-wrap text-[15px] leading-relaxed">
                {message.content}
              </div>
            ) : (
              <div className="prose prose-sm max-w-none dark:prose-invert prose-p:leading-relaxed prose-pre:bg-gray-50 prose-pre:text-gray-900 prose-pre:border prose-pre:border-gray-200">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.content}</ReactMarkdown>
              </div>
            )}
          </div>

          {/* Sources (only for assistant) */}
          {!isUser && message.sources && message.sources.length > 0 && (
            <div className="mt-1 flex flex-wrap gap-2">
              {message.sources.map((source, idx) => (
                <div
                  key={`${source.fileId}-${idx}`}
                  className="flex items-center gap-1.5 rounded-full border border-blue-100 bg-blue-50/50 px-3 py-1 text-xs text-blue-700 transition-colors hover:bg-blue-100/50"
                  title={source.originalName}
                >
                  <FileText className="h-3 w-3" />
                  <span className="max-w-[150px] truncate font-medium">{source.originalName}</span>
                </div>
              ))}
            </div>
          )}

          {/* Timestamp */}
          <span className="px-1 text-[11px] text-gray-400">
            {format(new Date(message.createdAt), "HH:mm")}
          </span>
        </div>
      </div>
    </div>
  );
}
