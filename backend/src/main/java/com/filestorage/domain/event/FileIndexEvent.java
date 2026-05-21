package com.filestorage.domain.event;

import java.util.UUID;

public record FileIndexEvent(UUID fileId, UUID tenantId, UUID userId, Action action) {

    public enum Action {
        UPSERT_CONTENT,
        UPSERT_METADATA,
        DELETE
    }
}
