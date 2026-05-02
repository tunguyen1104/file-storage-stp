package com.filestorage.adapter.in.rest.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PartUrlResponse {

    private String uploadId;
    private int partNumber;
    private String partUrl;
    private Instant expiresAt;
}
