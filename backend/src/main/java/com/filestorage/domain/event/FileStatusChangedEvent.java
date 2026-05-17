package com.filestorage.domain.event;

import com.filestorage.domain.model.enums.FileStatus;

import java.util.UUID;

/**
 * Emitted whenever a file transitions to a terminal status
 * ({@link FileStatus#ACTIVE}, {@link FileStatus#INFECTED}, or {@link FileStatus#UPLOAD_FAILED}).
 * <p>
 * Consumed by the SSE infrastructure to push real-time updates to connected clients
 * waiting on {@code GET /api/v1/files/{fileId}/status/stream}.
 */
public record FileStatusChangedEvent(
        UUID fileId,
        UUID tenantId,
        UUID userId,
        FileStatus status
) {
}
