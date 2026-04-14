package com.filestorage.shared.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_PAYLOAD(1, "errors.invalid-payload", HttpStatus.BAD_REQUEST),
    ACCESS_DENIED(2, "errors.access-denied", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(1000, "error.invalid-credential", HttpStatus.UNAUTHORIZED),
    EMAIL_ALREADY_INUSE(1001, "Email already used", HttpStatus.BAD_REQUEST),
    TOKEN_EXPIRED(1002, "Token expired", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(1003, "Invalid Token or Already logout", HttpStatus.UNAUTHORIZED),
    USER_NOT_FOUND(1004, "User not found", HttpStatus.NOT_FOUND),
    PASSWORD_MISMATCH(1005, "Passwords do not match", HttpStatus.BAD_REQUEST),
    WRONG_PASSWORD(1006, "Current password is incorrect", HttpStatus.BAD_REQUEST),
    ROLE_NOT_FOUND(1007, "Role not found", HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_SERVER_ERROR(9999, "errors.server-error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String key;
    private final HttpStatus httpStatus;
}

