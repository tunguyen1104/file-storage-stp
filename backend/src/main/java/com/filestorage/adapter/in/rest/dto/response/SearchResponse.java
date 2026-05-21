package com.filestorage.adapter.in.rest.dto.response;

import java.util.List;
import java.util.Map;

/**
 * Top-level full-text search response. {@code aggregations.mimeTypeCounts}
 * is empty for {@code type=FOLDER}.
 */
public record SearchResponse(
        List<SearchHitResponse> items,
        long totalHits,
        int page,
        int size,
        Map<String, Map<String, Long>> aggregations
) {
}
