package com.filestorage.domain.model.rag;

import java.util.UUID;

public record RagSourceView(
        UUID fileId,
        String originalName,
        int chunkIndex,
        String snippet,
        Double score
) {
}
