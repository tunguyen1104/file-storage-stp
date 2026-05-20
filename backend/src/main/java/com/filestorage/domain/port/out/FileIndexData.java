package com.filestorage.domain.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain-side payload for indexing a single file into the search backend.
 *
 * <p>Pure Java — no ES / Spring dependency. The adapter layer maps this into the
 * concrete {@code FileDocument} before talking to Elasticsearch.
 *
 * <p>{@code content} carries the already-extracted plain text. {@code null}
 * means the MIME type was not extractable; the search backend should leave the
 * content field unset on the document.
 */
public record FileIndexData(
        UUID id,
        UUID tenantId,
        UUID folderId,
        UUID uploadedBy,
        String originalName,
        String mimeType,
        String content,
        Instant createdAt
) {
}
