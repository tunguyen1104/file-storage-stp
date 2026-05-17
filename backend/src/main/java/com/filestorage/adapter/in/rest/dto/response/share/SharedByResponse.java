package com.filestorage.adapter.in.rest.dto.response.share;

import java.util.UUID;

public record SharedByResponse(
        UUID id,
        String fullName
) {
}
