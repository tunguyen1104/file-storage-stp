package com.filestorage.domain.port.in.share;

import com.filestorage.domain.model.enums.ShareSortBy;
import com.filestorage.domain.model.enums.ShareStatus;
import com.filestorage.domain.model.enums.SortDirection;

import java.util.List;
import java.util.UUID;

public record GetAdminSharesQuery(
        UUID adminId,
        List<UUID> tenantIds,
        List<UUID> sharedByIds,
        String resourceType,
        List<ShareStatus> statuses,
        int page,
        int limit,
        List<ShareSortBy> sortBy,
        List<SortDirection> sortDirection
) {
}
