package com.filestorage.domain.model.rag;

import com.filestorage.domain.model.enums.FileStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RagChunkIndexData(
        UUID fileId,
        UUID tenantId,
        UUID folderId,
        UUID uploadedBy,
        String originalName,
        int chunkIndex,
        UUID ingestionId,
        String content,
        List<Float> contentVector,
        String sourceKey,
        String embeddingModel,
        String chunkerVersion,
        boolean ready,
        boolean retrievable,
        FileStatus fileStatus,
        Instant createdAt
) {

    public RagChunkIndexData {
        Objects.requireNonNull(fileId, "fileId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(uploadedBy, "uploadedBy must not be null");
        Objects.requireNonNull(ingestionId, "ingestionId must not be null");
        Objects.requireNonNull(fileStatus, "fileStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        requireText(originalName, "originalName");
        requireText(content, "content");
        requireText(sourceKey, "sourceKey");
        requireText(embeddingModel, "embeddingModel");
        requireText(chunkerVersion, "chunkerVersion");
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must be non-negative");
        }
        contentVector = List.copyOf(Objects.requireNonNull(contentVector, "contentVector must not be null"));
        if (contentVector.isEmpty()) {
            throw new IllegalArgumentException("contentVector must not be empty");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
