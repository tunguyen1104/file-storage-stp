package com.filestorage.domain.model.search;

import java.util.List;
import java.util.Map;

/**
 * Top-level full-text search result.
 *
 * <p>{@code aggregations} keys (only {@code mimeTypeCounts} for now) are sourced
 * from the files query exclusively. For {@link SearchType#FOLDER} requests the
 * map is empty (never {@code null}).
 */
public record SearchView(
        List<SearchHitView> items,
        long totalHits,
        int page,
        int size,
        Map<String, Map<String, Long>> aggregations
) {
}
