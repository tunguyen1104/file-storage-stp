package com.filestorage.domain.model.share;

public record PaginationView(
        int page,
        int limit,
        long total,
        int totalPages
) {
}
