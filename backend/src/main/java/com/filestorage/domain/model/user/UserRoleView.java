package com.filestorage.domain.model.user;

import com.filestorage.domain.model.enums.RoleCode;

import java.util.UUID;

public record UserRoleView(
        UUID id,
        RoleCode code,
        String name
) {
}

