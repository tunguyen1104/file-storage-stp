package com.filestorage.adapter.in.rest.dto.response;

import com.filestorage.domain.model.enums.FileStatus;

import java.time.Instant;
import java.util.UUID;

public record FileStatusResponse(
        UUID fileId,
        FileStatus status,
        String originalName,
        Instant updatedAt
) {
}
