package com.filestorage.domain.model.rag;

import com.filestorage.domain.model.enums.RoleCode;

import java.util.List;
import java.util.UUID;

public record RagAccessScope(
        UUID tenantId,
        UUID userId,
        RoleCode role,
        List<UUID> sharedFileIds,
        List<UUID> sharedFolderIds
) {

    public RagAccessScope {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        sharedFileIds = sharedFileIds == null ? List.of() : List.copyOf(sharedFileIds);
        sharedFolderIds = sharedFolderIds == null ? List.of() : List.copyOf(sharedFolderIds);
    }
}
