package com.filestorage.domain.port.in.search;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.search.SearchType;

import java.util.UUID;

/**
 * Command record consumed by {@link SuggestUseCase}. Mirrors the smaller surface
 * of the SAYT endpoint — no pagination, no sort, no mime/date filters.
 *
 * <p>{@code userId} / {@code tenantId} / {@code role} are always sourced from
 * the JWT.
 */
public record SuggestQuery(
        String q,
        UUID userId,
        UUID tenantId,
        RoleCode role,
        SearchType type,
        int size
) {
}
