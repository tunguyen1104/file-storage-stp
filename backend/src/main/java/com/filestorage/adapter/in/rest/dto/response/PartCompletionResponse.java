package com.filestorage.adapter.in.rest.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PartCompletionResponse {

    private String uploadId;
    private int partNumber;
    private int uploadedPartsCount;
    private int totalParts;
}
