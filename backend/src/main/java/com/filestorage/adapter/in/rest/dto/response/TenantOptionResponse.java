package com.filestorage.adapter.in.rest.dto.response;

import java.util.UUID;

public record TenantOptionResponse(
        UUID id,
        String name
) {
}

