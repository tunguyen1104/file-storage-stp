package com.filestorage.domain.model.rag;

import com.filestorage.domain.model.enums.RagChatMessageRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RagChatMessageView(
        UUID id,
        RagChatMessageRole role,
        String content,
        String model,
        Integer retrievalTopK,
        Integer inputTokens,
        Integer outputTokens,
        Instant createdAt,
        List<RagSourceView> sources
) {
}
