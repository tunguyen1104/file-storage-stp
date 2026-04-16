package com.filestorage.domain.model.tenant;

import java.util.List;

public record TenantPageView(
        List<TenantView> data,
        int page,
        int limit,
        long total,
        int totalPages
) {
}

