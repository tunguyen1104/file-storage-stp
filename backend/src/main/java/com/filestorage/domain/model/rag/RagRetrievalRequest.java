package com.filestorage.domain.model.rag;

import com.filestorage.domain.model.enums.RoleCode;

import java.util.UUID;

public record RagRetrievalRequest(
        UUID tenantId,
        UUID userId,
        RoleCode role,
        String question,
        Integer topK
) {
}
