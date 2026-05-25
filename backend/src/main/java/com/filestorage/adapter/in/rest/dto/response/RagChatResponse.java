package com.filestorage.adapter.in.rest.dto.response;

public record RagChatResponse(
        RagChatSessionResponse session,
        RagChatMessageResponse userMessage,
        RagChatMessageResponse assistantMessage
) {
}
