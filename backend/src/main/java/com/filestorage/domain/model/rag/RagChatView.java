package com.filestorage.domain.model.rag;

public record RagChatView(
        RagChatSessionView session,
        RagChatMessageView userMessage,
        RagChatMessageView assistantMessage
) {
}
