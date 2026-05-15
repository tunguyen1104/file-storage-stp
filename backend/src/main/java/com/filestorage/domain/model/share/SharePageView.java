package com.filestorage.domain.model.share;

import java.util.List;

public record SharePageView(
        List<ShareWithResourceView> data,
        PaginationView pagination
) {
}
