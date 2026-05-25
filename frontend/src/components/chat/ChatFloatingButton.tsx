"use client";

import React from "react";
import { MessageSquare, X } from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";
import { useChat } from "@/contexts/chat-context";
import { cn } from "@/lib/utils";

export function ChatFloatingButton() {
  const { isOpen, toggleChat } = useChat();

  return (
    <div className="fixed bottom-6 right-6 z-50">
      <AnimatePresence mode="wait">
        <motion.button
          key={isOpen ? "close" : "open"}
          initial={{ scale: 0.8, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          exit={{ scale: 0.8, opacity: 0 }}
          whileHover={{ scale: 1.05 }}
          whileTap={{ scale: 0.95 }}
          onClick={toggleChat}
          className={cn(
            "flex h-14 w-14 items-center justify-center rounded-full shadow-xl transition-colors",
            isOpen 
              ? "bg-gray-800 text-white hover:bg-gray-900" 
              : "bg-gradient-to-r from-blue-600 to-indigo-600 text-white hover:from-blue-700 hover:to-indigo-700"
          )}
          aria-label={isOpen ? "Đóng chat" : "Mở trợ lý AI"}
        >
          {isOpen ? (
            <X className="h-6 w-6" />
          ) : (
            <MessageSquare className="h-6 w-6" />
          )}
        </motion.button>
      </AnimatePresence>
    </div>
  );
}
