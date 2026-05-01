package com.filestorage.domain.model.folder;

import java.util.UUID;

public record BreadcrumbView(
        UUID id,
        String name
) {
}

