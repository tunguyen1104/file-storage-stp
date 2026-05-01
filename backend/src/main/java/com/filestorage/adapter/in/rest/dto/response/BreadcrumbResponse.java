package com.filestorage.adapter.in.rest.dto.response;

import java.util.UUID;

public record BreadcrumbResponse(
        UUID id,
        String name
) {
}

