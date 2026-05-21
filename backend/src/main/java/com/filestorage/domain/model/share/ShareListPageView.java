package com.filestorage.domain.model.share;

import java.util.List;

public record ShareListPageView(
        List<ShareView> data,
        PaginationView pagination
) {
}
