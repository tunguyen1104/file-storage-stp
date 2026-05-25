package com.filestorage.domain.model.rag;

import java.util.List;

public record RagChatMessagePageView(
        List<RagChatMessageView> data,
        int page,
        int size,
        long total,
        int totalPages
) {
}
