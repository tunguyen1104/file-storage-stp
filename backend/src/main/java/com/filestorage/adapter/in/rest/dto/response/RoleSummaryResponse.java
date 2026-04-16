package com.filestorage.adapter.in.rest.dto.response;

import com.filestorage.domain.model.enums.RoleCode;

import java.util.UUID;

public record RoleSummaryResponse(
        UUID id,
        RoleCode code,
        String name
) {
}

