package com.filestorage.adapter.in.rest.dto.request;

import com.filestorage.domain.model.enums.SortDirection;
import com.filestorage.domain.model.search.SearchSortBy;
import com.filestorage.domain.model.search.SearchType;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Binds query parameters for {@code GET /api/v1/search}. Bound via
 * {@code @ModelAttribute} so spring-mvc populates fields by query-string name.
 *
 * <p>Defaults are applied here so the controller doesn't have to know about them.
 * Note: {@code userId} / {@code tenantId} / {@code role} are NOT bound from the
 * request — they come from the JWT in the controller.
 */
@Getter
@Setter
public class SearchRequest {

    private String q;
    private SearchType type = SearchType.ALL;
    private List<String> mimeType;
    private Instant dateFrom;
    private Instant dateTo;
    private Integer page = 1;
    private Integer size = 20;
    private SearchSortBy sort = SearchSortBy.SCORE;
    private SortDirection sortDir = SortDirection.DESC;
}
