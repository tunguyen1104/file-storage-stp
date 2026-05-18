package com.filestorage.domain.model.share;

import java.util.UUID;

public record ShareResourceView(
        String type,
        UUID id,
        String name,
        String mimeType,
        Long sizeBytes
) {
}
