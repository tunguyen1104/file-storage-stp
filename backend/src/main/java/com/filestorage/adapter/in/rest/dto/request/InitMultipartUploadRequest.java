package com.filestorage.adapter.in.rest.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class InitMultipartUploadRequest {

    @NotBlank
    private String originalName;

    @NotBlank
    private String mimeType;

    @NotNull
    @Min(1)
    private Long totalSizeBytes;

    @NotNull
    @Min(1)
    private Integer totalParts;

    private UUID folderId;
}
