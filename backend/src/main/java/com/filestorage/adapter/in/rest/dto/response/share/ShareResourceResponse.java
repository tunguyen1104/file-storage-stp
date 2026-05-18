package com.filestorage.adapter.in.rest.dto.response.share;

import java.util.UUID;

public record ShareResourceResponse(
        String type,
        UUID id,
        String name,
        String mimeType,
        Long sizeBytes
) {
}
