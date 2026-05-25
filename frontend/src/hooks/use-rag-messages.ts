"use client";

import useSWR from "swr";
import { ragApi } from "@/lib/api/rag";
import { API_ROUTES } from "@/lib/constants";
import type { RagChatMessageResponse } from "@/types/rag";

export function useRagMessages(sessionId: string | null, page = 1, size = 50) {
  const shouldFetch = !!sessionId;

  const { data, error, isLoading, mutate } = useSWR(
    shouldFetch ? [API_ROUTES.RAG.SESSION_MESSAGES(sessionId!), page, size] : null,
    ([, p, s]) => ragApi.getMessages(sessionId!, p as number, s as number)
  );

  const sendMessage = async (message: string, topK?: number) => {
    if (!sessionId) return null;

    // Optimistically update the UI if needed
    // The API is synchronous so it will take some time to return
    // Let's rely on the real response for simplicity or we can add a fake message
    
    const response = await ragApi.sendMessage(sessionId, message, topK);
    await mutate(); // Refresh the message list
    return response;
  };

  return {
    messages: data?.data || [],
    pagination: {
      pageSize: data?.pageSize || size,
      totalPages: data?.totalPages || 1,
      totalElements: data?.totalElements || 0,
      currentPage: data?.currentPage || page,
    },
    isLoading,
    error,
    sendMessage,
    mutate,
  };
}
