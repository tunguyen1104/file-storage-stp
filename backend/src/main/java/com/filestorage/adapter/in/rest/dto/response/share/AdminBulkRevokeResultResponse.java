package com.filestorage.adapter.in.rest.dto.response.share;

import java.util.List;
import java.util.UUID;

public record AdminBulkRevokeResultResponse(
        UUID targetUserId,
        String scope,
        List<UUID> revokedShareIds,
        int revokedCount,
        UUID auditId
) {
}
