package com.filestorage.adapter.in.rest.mapper;

import com.filestorage.adapter.in.rest.dto.response.SearchHitResponse;
import com.filestorage.adapter.in.rest.dto.response.SearchResponse;
import com.filestorage.adapter.in.rest.dto.response.SuggestHitResponse;
import com.filestorage.adapter.in.rest.dto.response.SuggestResponse;
import com.filestorage.domain.model.search.SearchHitView;
import com.filestorage.domain.model.search.SearchView;
import com.filestorage.domain.model.search.SuggestHitView;
import com.filestorage.domain.model.search.SuggestView;
import com.filestorage.infrastructure.config.GlobalMapperConfig;
import org.mapstruct.Mapper;

/**
 * MapStruct mapping between domain search views and REST response DTOs.
 * {@code tenantId} on hits is dropped — the caller already knows their tenant.
 */
@Mapper(config = GlobalMapperConfig.class)
public interface SearchRestMapper {

    SearchResponse toSearchResponse(SearchView view);

    SearchHitResponse toSearchHitResponse(SearchHitView view);

    SuggestResponse toSuggestResponse(SuggestView view);

    SuggestHitResponse toSuggestHitResponse(SuggestHitView view);
}
