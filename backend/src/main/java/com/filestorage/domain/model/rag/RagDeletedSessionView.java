package com.filestorage.domain.model.rag;

import java.util.UUID;

public record RagDeletedSessionView(
        UUID id,
        boolean deleted
) {
}
