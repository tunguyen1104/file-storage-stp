package com.filestorage.adapter.in.rest.dto.response;

import com.filestorage.domain.model.search.SearchType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-hit response for the full-text search endpoint. {@code folderPath} is the
 * human-readable chain shown next to the name, e.g. {@code "Reports / Q4"};
 * {@code null} for root-level items.
 */
public record SearchHitResponse(
        UUID id,
        SearchType type,
        String name,
        String mimeType,
        UUID folderId,
        String folderPath,
        Instant createdAt,
        double score,
        Map<String, List<String>> highlights
) {
}
