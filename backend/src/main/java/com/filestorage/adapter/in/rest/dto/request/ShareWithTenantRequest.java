package com.filestorage.adapter.in.rest.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ShareWithTenantRequest(
        @NotNull UUID resourceId,
        @NotNull @Positive Long expireTime
) {
}
