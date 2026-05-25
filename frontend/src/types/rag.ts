export interface RagChatSessionResponse {
  id: string;
  title: string;
  status: "ACTIVE" | "ARCHIVED" | "DELETED";
  createdAt: string;
  updatedAt: string;
  lastMessageAt: string | null;
  deletedAt: string | null;
}

export interface RagSourceResponse {
  fileId: string;
  originalName: string;
}

export interface RagChatMessageResponse {
  id: string;
  role: "USER" | "ASSISTANT";
  content: string;
  model: string | null;
  retrievalTopK: number | null;
  inputTokens: number | null;
  outputTokens: number | null;
  createdAt: string;
  sources: RagSourceResponse[];
}

export interface RagChatMessageRequest {
  message: string;
  topK?: number;
}

export interface RagChatSessionRenameRequest {
  title: string;
}

export interface RagSendMessageResponse {
  session: RagChatSessionResponse;
  userMessage: RagChatMessageResponse;
  assistantMessage: RagChatMessageResponse;
}
