package com.filestorage.adapter.in.rest.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.filestorage.domain.model.enums.FileStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MultipartCompleteResponse {

    private UUID fileId;
    private FileStatus status;
    private String message;
}
