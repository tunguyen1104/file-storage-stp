package com.filestorage.domain.model.share;

import java.util.UUID;

public record SharedByView(
        UUID id,
        String fullName
) {
}
