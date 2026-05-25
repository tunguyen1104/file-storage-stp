package com.filestorage.adapter.in.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameRagChatSessionRequest(
        @NotBlank
        @Size(max = 255) String title
) {
}
