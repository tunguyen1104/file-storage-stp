package com.filestorage.adapter.in.rest.dto.request;

import jakarta.validation.constraints.Size;

public record CreateRagChatSessionRequest(
        @Size(max = 255) String title
) {
}
