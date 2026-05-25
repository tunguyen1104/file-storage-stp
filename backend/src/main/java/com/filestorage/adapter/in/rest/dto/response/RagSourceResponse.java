package com.filestorage.adapter.in.rest.dto.response;

import java.util.UUID;

public record RagSourceResponse(
        UUID fileId,
        String originalName
) {
}
