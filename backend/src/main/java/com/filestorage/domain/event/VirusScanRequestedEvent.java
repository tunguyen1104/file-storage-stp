package com.filestorage.domain.event;

import java.util.UUID;

public record VirusScanRequestedEvent(
        UUID fileId,
        String storageKey,
        UUID tenantId,
        UUID userId
) {
}
