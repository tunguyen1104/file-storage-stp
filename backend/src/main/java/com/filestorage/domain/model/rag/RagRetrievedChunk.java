package com.filestorage.domain.model.rag;

import java.util.UUID;

public record RagRetrievedChunk(
        String id,
        UUID fileId,
        UUID tenantId,
        UUID folderId,
        UUID uploadedBy,
        String originalName,
        int chunkIndex,
        String content,
        String sourceKey,
        double score,
        double vectorScore,
        double bm25Score
) {
}
