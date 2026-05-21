package com.filestorage.domain.port.out;

import java.time.Instant;
import java.util.UUID;

public record FolderIndexData(
        UUID id,
        UUID tenantId,
        UUID parentId,
        UUID createdBy,
        String name,
        Instant createdAt
) {
}
