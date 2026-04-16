package com.filestorage.adapter.in.rest.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(
        @Size(max = 255) String name,
        @Size(max = 100) @Pattern(regexp = "^[a-z0-9-]+$") String slug,
        @Positive Long storageQuotaBytes
) {
}

