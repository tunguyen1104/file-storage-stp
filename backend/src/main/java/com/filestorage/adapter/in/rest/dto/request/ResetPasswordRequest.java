package com.filestorage.adapter.in.rest.dto.request;

import com.filestorage.infrastructure.security.PasswordConstraint;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank String token,
        @PasswordConstraint String newPassword,
        @PasswordConstraint String confirmPassword
) {
}

