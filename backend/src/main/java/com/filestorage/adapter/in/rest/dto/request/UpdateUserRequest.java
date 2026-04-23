package com.filestorage.adapter.in.rest.dto.request;

import com.filestorage.domain.model.enums.RoleCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 255) String fullName,
        @Email String email,
        RoleCode roleCode,
        @Positive Long storageQuotaBytes
) {
}

