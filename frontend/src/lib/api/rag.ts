import { apiClient } from "@/lib/api-client";
import { API_ROUTES } from "@/lib/constants";
import type { 
  RagChatSessionResponse, 
  RagChatMessageResponse, 
  RagSendMessageResponse 
} from "@/types/rag";
import type { PageResponse } from "@/types/api.types";

export const ragApi = {
  createSession: async (): Promise<RagChatSessionResponse> => {
    const { data } = await apiClient.post<{ data: RagChatSessionResponse; success: boolean }>(
      API_ROUTES.RAG.SESSIONS
    );
    return data.data;
  },

  listSessions: async (page = 1, size = 20): Promise<PageResponse<RagChatSessionResponse>> => {
    const { data } = await apiClient.get<{ data: PageResponse<RagChatSessionResponse>; success: boolean }>(
      API_ROUTES.RAG.SESSIONS,
      { params: { page, size } }
    );
    return data.data;
  },

  renameSession: async (sessionId: string, title: string): Promise<RagChatSessionResponse> => {
    const { data } = await apiClient.patch<{ data: RagChatSessionResponse; success: boolean }>(
      API_ROUTES.RAG.SESSION_BY_ID(sessionId),
      { title }
    );
    return data.data;
  },

  deleteSession: async (sessionId: string): Promise<{ id: string; deleted: boolean }> => {
    const { data } = await apiClient.delete<{ data: { id: string; deleted: boolean }; success: boolean }>(
      API_ROUTES.RAG.SESSION_BY_ID(sessionId)
    );
    return data.data;
  },

  getMessages: async (sessionId: string, page = 1, size = 50): Promise<PageResponse<RagChatMessageResponse>> => {
    const { data } = await apiClient.get<{ data: PageResponse<RagChatMessageResponse>; success: boolean }>(
      API_ROUTES.RAG.SESSION_MESSAGES(sessionId),
      { params: { page, size } }
    );
    return data.data;
  },

  sendMessage: async (sessionId: string, message: string, topK?: number): Promise<RagSendMessageResponse> => {
    const { data } = await apiClient.post<{ data: RagSendMessageResponse; success: boolean }>(
      API_ROUTES.RAG.SESSION_MESSAGES(sessionId),
      { message, topK }
    );
    return data.data;
  }
};
