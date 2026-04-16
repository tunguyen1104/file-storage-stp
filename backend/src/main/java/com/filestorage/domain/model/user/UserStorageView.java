package com.filestorage.domain.model.user;

import java.util.UUID;

public record UserStorageView(
        UUID userId,
        long storageQuotaBytes,
        long storageUsedBytes,
        long storageAvailableBytes,
        double usagePercent
) {
}

