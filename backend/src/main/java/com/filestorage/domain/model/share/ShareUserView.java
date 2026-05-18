package com.filestorage.domain.model.share;

import java.util.UUID;

public record ShareUserView(
        UUID id,
        String email,
        String fullName
) {
}
