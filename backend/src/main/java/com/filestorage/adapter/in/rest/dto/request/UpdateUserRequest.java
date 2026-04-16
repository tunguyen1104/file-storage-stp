package com.filestorage.adapter.in.rest.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateUserRequest(
        @Size(max = 255) String fullName,
        @Email String email,
        UUID roleId,
        @Positive Long storageQuotaBytes
) {
}

