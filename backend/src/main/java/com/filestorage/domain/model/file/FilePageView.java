package com.filestorage.domain.model.file;

import java.util.List;

public record FilePageView(
        List<FileView> data,
        int page,
        int limit,
        long total,
        int totalPages
) {
}

