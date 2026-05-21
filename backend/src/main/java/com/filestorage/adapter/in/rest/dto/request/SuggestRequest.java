package com.filestorage.adapter.in.rest.dto.request;

import com.filestorage.domain.model.search.SearchType;
import lombok.Getter;
import lombok.Setter;

/**
 * Binds query parameters for {@code GET /api/v1/search/suggest}.
 *
 * <p>{@code size} caps and validation live in {@code SuggestService}; this DTO
 * just carries the raw values from the wire.
 */
@Getter
@Setter
public class SuggestRequest {

    private String q;
    private SearchType type = SearchType.ALL;
    private Integer size;
}
