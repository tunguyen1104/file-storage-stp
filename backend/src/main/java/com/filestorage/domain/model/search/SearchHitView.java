package com.filestorage.domain.model.search;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-hit projection returned by the full-text search endpoint.
 *
 * <p>{@code folderPath} is a human-readable chain such as
 * {@code "Documents / Reports / Q4"} — built once per search (one recursive CTE)
 * and cached alongside the result. Root-level items get {@code null}.
 *
 * @param type one of {@link SearchType#FILE} or {@link SearchType#FOLDER};
 *             never {@link SearchType#ALL}. Derived from the ES index the hit
 *             came from — the value is not stored inside ES documents.
 */
public record SearchHitView(
        UUID id,
        SearchType type,
        UUID tenantId,
        String name,
        String mimeType,
        UUID folderId,
        String folderPath,
        Instant createdAt,
        double score,
        Map<String, List<String>> highlights
) {
}
