package com.filestorage.domain.port.in.search;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.enums.SortDirection;
import com.filestorage.domain.model.search.SearchSortBy;
import com.filestorage.domain.model.search.SearchType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Command record consumed by {@link SearchUseCase}.
 *
 * <p>{@code userId} / {@code tenantId} / {@code role} are always sourced from
 * the JWT — never from query params — and populated by the controller layer
 * before the use case is invoked.
 */
public record SearchQuery(
        String q,
        UUID userId,
        UUID tenantId,
        RoleCode role,
        SearchType type,
        List<String> mimeTypes,
        Instant dateFrom,
        Instant dateTo,
        int page,
        int size,
        SearchSortBy sort,
        SortDirection sortDirection
) {
}
