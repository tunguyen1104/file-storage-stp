package com.filestorage.adapter.in.rest.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse<T> {
    private T errors;
    private boolean success;
    private int code;

    public static <T> ErrorResponse<T> buildError(T errors, int code) {
        return ErrorResponse.<T>builder()
                .errors(errors)
                .success(false)
                .code(code)
                .build();
    }
}
