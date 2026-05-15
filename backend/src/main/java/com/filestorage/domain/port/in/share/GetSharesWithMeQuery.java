package com.filestorage.domain.port.in.share;

import java.util.UUID;

public record GetSharesWithMeQuery(
        UUID actorId,
        UUID actorTenantId,
        String resourceType,
        int page,
        int limit
) {
}
