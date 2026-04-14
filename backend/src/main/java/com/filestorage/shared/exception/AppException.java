package com.filestorage.shared.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {

    private final ErrorCode error;

    public AppException(ErrorCode error) {
        super(error.getKey());
        this.error = error;
    }
}

