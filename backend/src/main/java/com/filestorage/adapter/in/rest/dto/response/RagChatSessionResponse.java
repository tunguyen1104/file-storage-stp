package com.filestorage.adapter.in.rest.dto.response;

import com.filestorage.domain.model.enums.RagChatSessionStatus;

import java.time.Instant;
import java.util.UUID;

public record RagChatSessionResponse(
        UUID id,
        String title,
        RagChatSessionStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant lastMessageAt,
        Instant deletedAt
) {
}
