package com.filestorage.domain.model.rag;

import com.filestorage.domain.model.enums.RagChatSessionStatus;

import java.time.Instant;
import java.util.UUID;

public record RagChatSessionView(
        UUID id,
        String title,
        RagChatSessionStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant lastMessageAt,
        Instant deletedAt
) {
}
