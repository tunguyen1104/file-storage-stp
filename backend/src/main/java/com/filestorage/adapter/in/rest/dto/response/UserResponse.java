package com.filestorage.adapter.in.rest.dto.response;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String role,
        UUID tenantId
) {
}

