package com.filestorage.adapter.in.rest.dto.response.share;

import java.util.UUID;

public record ShareUserResponse(
        UUID id,
        String email,
        String fullName
) {
}
