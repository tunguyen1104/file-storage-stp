package com.filestorage.domain.model.rag;

public record ChatPromptMessage(
        String role,
        String content
) {
}
