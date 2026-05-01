package com.filestorage.domain.model.folder;

import java.util.List;

public record FolderItemPageView(
        List<FolderItemView> data,
        int page,
        int size,
        long total,
        int totalPages
) {
}

