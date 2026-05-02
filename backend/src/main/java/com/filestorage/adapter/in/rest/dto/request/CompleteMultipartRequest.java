package com.filestorage.adapter.in.rest.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class CompleteMultipartRequest {

    @NotEmpty
    @Valid
    private List<PartItem> parts;

    @Getter
    @NoArgsConstructor
    public static class PartItem {

        @NotNull
        private Integer partNumber;

        @NotNull
        private String etag;
    }
}
