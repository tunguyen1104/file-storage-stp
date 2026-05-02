package com.filestorage.adapter.in.rest.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.filestorage.domain.model.enums.MultipartUploadStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MultipartSessionResponse {

    private String uploadId;
    private UUID fileId;
    private MultipartUploadStatus status;
    private int totalParts;
    private List<PartInfo> uploadedParts;
    private Instant expiresAt;

    @Getter
    @Builder
    public static class PartInfo {
        private int partNumber;
        private String etag;
    }
}
