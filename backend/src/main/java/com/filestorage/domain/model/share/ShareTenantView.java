package com.filestorage.domain.model.share;

import java.util.UUID;

public record ShareTenantView(
        UUID id,
        String name
) {
}
