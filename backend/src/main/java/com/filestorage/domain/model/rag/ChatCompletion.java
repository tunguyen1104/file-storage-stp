package com.filestorage.domain.model.rag;

public record ChatCompletion(
        String content,
        String model,
        Integer inputTokens,
        Integer outputTokens
) {
}
