package com.filestorage.adapter.in.rest.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RagChatRequest(
        @NotBlank String message,
        @Min(1) Integer topK
) {
}
