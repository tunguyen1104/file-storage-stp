package com.filestorage.adapter.in.rest.dto.request;

import com.filestorage.infrastructure.security.PasswordConstraint;
import jakarta.validation.constraints.NotBlank;

public record ChangeUserPasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @PasswordConstraint String newPassword,
        @NotBlank @PasswordConstraint String confirmPassword
) {
}

