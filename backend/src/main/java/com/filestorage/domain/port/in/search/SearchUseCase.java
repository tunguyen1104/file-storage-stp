package com.filestorage.domain.port.in.search;

import com.filestorage.domain.model.search.SearchView;

/**
 * Inbound port for the fuzzy full-text search endpoint (the Enter-key path).
 *
 * <p>Implementation handles cache lookup, role-aware ES query construction,
 * folder-path resolution, and result caching.
 */
public interface SearchUseCase {

    SearchView execute(SearchQuery query);
}
