"use client";

import useSWR from "swr";
import { ragApi } from "@/lib/api/rag";
import { API_ROUTES } from "@/lib/constants";
import type { RagChatSessionResponse } from "@/types/rag";

export function useRagSessions(page = 1, size = 20) {
  const { data, error, isLoading, mutate } = useSWR(
    [API_ROUTES.RAG.SESSIONS, page, size],
    ([, p, s]) => ragApi.listSessions(p as number, s as number)
  );

  const createSession = async () => {
    const newSession = await ragApi.createSession();
    await mutate(); // Refresh the list
    return newSession;
  };

  const renameSession = async (sessionId: string, title: string) => {
    const updated = await ragApi.renameSession(sessionId, title);
    await mutate(); // Refresh the list
    return updated;
  };

  const deleteSession = async (sessionId: string) => {
    await ragApi.deleteSession(sessionId);
    await mutate(); // Refresh the list
  };

  return {
    sessions: data?.data || [],
    pagination: {
      pageSize: data?.pageSize || size,
      totalPages: data?.totalPages || 1,
      totalElements: data?.totalElements || 0,
      currentPage: data?.currentPage || page,
    },
    isLoading,
    error,
    createSession,
    renameSession,
    deleteSession,
    mutate,
  };
}
