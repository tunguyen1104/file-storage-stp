package com.filestorage.adapter.in.rest.dto.response;

import java.util.UUID;

public record UserSearchResponse(
        UUID id,
        String email
) {
}
