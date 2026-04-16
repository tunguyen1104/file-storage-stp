package com.filestorage.adapter.in.rest.dto.response;

import java.util.UUID;

public record UserStorageResponse(
        UUID userId,
        long storageQuotaBytes,
        long storageUsedBytes,
        long storageAvailableBytes,
        double usagePercent
) {
}

