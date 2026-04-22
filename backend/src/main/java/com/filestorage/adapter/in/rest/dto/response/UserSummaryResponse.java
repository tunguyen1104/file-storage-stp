package com.filestorage.adapter.in.rest.dto.response;

import java.util.UUID;

public record UserSummaryResponse(
        UUID id,
        String fullName
) {
}

