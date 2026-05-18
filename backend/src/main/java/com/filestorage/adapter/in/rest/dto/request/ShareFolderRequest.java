package com.filestorage.adapter.in.rest.dto.request;

import com.filestorage.domain.model.enums.PermissionFile;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ShareFolderRequest(
        @NotNull UUID folderId,
        @NotBlank @Email String sharedWithUserEmail,
        @NotNull PermissionFile permission,
        @NotNull @Positive Long expireTime
) {
}
