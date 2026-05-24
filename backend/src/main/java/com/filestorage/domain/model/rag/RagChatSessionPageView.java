package com.filestorage.domain.model.rag;

import java.util.List;

public record RagChatSessionPageView(
        List<RagChatSessionView> data,
        int page,
        int size,
        long total,
        int totalPages
) {
}
