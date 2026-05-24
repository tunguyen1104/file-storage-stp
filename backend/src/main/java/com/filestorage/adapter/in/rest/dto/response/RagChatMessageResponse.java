package com.filestorage.adapter.in.rest.dto.response;

import com.filestorage.domain.model.enums.RagChatMessageRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RagChatMessageResponse(
        UUID id,
        RagChatMessageRole role,
        String content,
        String model,
        Integer retrievalTopK,
        Integer inputTokens,
        Integer outputTokens,
        Instant createdAt,
        List<RagSourceResponse> sources
) {
}
