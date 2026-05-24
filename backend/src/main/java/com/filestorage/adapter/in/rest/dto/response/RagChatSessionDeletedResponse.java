package com.filestorage.adapter.in.rest.dto.response;

import java.util.UUID;

public record RagChatSessionDeletedResponse(
        UUID id,
        boolean deleted
) {
}
