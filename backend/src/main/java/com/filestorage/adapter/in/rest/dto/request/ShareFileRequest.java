package com.filestorage.adapter.in.rest.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ShareFileRequest(
        @NotNull UUID fileId,
        @NotBlank @Email String sharedWithUserEmail,
        @NotNull @Positive Long expireTime
) {
}
