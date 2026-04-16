package com.filestorage.adapter.in.rest.dto.response;

public record UserPaginationResponse(
        int page,
        int limit,
        long total,
        int totalPages
) {
}

