package com.filestorage.domain.event;

import java.util.UUID;

public record FolderIndexEvent(UUID folderId, UUID tenantId, UUID userId, Action action) {

    public enum Action {
        UPSERT,
        DELETE
    }
}
