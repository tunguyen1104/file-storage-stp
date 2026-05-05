package com.filestorage.adapter.in.rest.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String name,
        String slug,
        long storageQuotaBytes,
        long storageUsedBytes,
        boolean isActive,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long userCount,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {

    public TenantResponse(
            UUID id,
            String name,
            String slug,
            long storageQuotaBytes,
            long storageUsedBytes,
            boolean isActive,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, name, slug, storageQuotaBytes, storageUsedBytes, isActive, null, createdBy, createdAt, updatedAt);
    }
}
