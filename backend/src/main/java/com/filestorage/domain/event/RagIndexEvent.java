package com.filestorage.domain.event;

import java.util.UUID;

public record RagIndexEvent(UUID fileId, UUID tenantId, UUID userId, Action action) {

    public enum Action {
        INGEST
    }
}
