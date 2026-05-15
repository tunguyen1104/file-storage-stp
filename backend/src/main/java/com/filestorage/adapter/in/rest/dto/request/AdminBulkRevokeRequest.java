package com.filestorage.adapter.in.rest.dto.request;

import com.filestorage.domain.model.enums.ShareRevokeScope;
import jakarta.validation.constraints.NotNull;

public record AdminBulkRevokeRequest(
        @NotNull ShareRevokeScope scope
) {
}
