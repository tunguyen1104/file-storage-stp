package com.filestorage.domain.model.tenant;

import java.util.UUID;

public record TenantOptionView(
        UUID id,
        String name
) {
}

