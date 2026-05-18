package com.filestorage.domain.model.share;

import java.util.List;
import java.util.UUID;

public record AdminBulkRevokeResultView(
        UUID targetUserId,
        String scope,
        List<UUID> revokedShareIds,
        int revokedCount,
        UUID auditId
) {
}
