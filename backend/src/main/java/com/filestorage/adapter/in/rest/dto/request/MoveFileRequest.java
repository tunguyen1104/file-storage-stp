package com.filestorage.adapter.in.rest.dto.request;

import java.util.UUID;

public record MoveFileRequest(
        UUID folderId
) {
}

