package com.filestorage.domain.model.folder;

import java.util.List;

public record AdminFolderItemPageView(
        List<AdminFolderItemView> data,
        int page,
        int limit,
        long total,
        int totalPages
) {
}
