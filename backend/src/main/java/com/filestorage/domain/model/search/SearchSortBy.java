package com.filestorage.domain.model.search;

/**
 * Sort options for the full-text search endpoint. SAYT does not honour sort
 * choices — it always orders by relevance score.
 */
public enum SearchSortBy {
    SCORE,
    NAME,
    CREATED_AT
}
