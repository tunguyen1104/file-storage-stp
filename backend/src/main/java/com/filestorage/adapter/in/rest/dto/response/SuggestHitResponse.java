package com.filestorage.adapter.in.rest.dto.response;

import com.filestorage.domain.model.search.SearchType;

import java.util.UUID;

public record SuggestHitResponse(
        UUID id,
        SearchType type,
        String name,
        String mimeType,
        UUID folderId,
        String folderPath
) {
}
