package com.filestorage.domain.model.share;

import java.util.List;

public record SharedWithMePageView(
        List<SharedWithMeView> data,
        PaginationView pagination
) {
}
