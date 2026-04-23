package com.filestorage.adapter.in.rest.dto.request;

import com.filestorage.domain.model.enums.RoleCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(max = 255) String fullName,
        @NotNull RoleCode roleCode,
        @Positive Long storageQuotaBytes,
        java.util.UUID tenantId
) {
}

