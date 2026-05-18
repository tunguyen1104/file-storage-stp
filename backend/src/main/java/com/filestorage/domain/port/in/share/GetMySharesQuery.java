package com.filestorage.domain.port.in.share;

import com.filestorage.domain.model.enums.ShareStatus;

import java.util.UUID;

public record GetMySharesQuery(
        UUID actorId,
        String resourceType,
        ShareStatus status,
        int page,
        int limit
) {
}
