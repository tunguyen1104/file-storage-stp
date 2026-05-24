package com.filestorage.domain.model.rag;

public record RagTextChunk(
        int index,
        String content,
        int startOffset,
        int endOffset
) {

    public RagTextChunk {
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (startOffset < 0 || endOffset <= startOffset) {
            throw new IllegalArgumentException("invalid chunk offsets");
        }
    }
}
