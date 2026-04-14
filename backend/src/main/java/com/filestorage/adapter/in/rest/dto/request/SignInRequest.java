package com.filestorage.adapter.in.rest.dto.request;

import com.filestorage.infrastructure.security.PasswordConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SignInRequest(
        @NotBlank @Email String email,
        @NotBlank @PasswordConstraint String password
) {
}

