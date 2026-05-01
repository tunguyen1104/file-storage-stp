package com.filestorage.adapter.in.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameFileRequest(
        @NotBlank
        @Size(max = 500) String originalName
) {
}

