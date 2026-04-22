package com.filestorage.shared.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_PAYLOAD(1, "errors.invalid-payload", HttpStatus.BAD_REQUEST),
    ACCESS_DENIED(2, "errors.access-denied", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(3, "errors.forbidden", HttpStatus.FORBIDDEN),
    INVALID_CREDENTIALS(1000, "error.invalid-credential", HttpStatus.UNAUTHORIZED),
    EMAIL_ALREADY_INUSE(1001, "error.email-already-exists", HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS(1001, "error.email-already-exists", HttpStatus.CONFLICT),
    TOKEN_EXPIRED(1002, "Token expired", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(1003, "Invalid Token or Already logout", HttpStatus.UNAUTHORIZED),
    USER_NOT_FOUND(1004, "error.user-not-found", HttpStatus.NOT_FOUND),
    PASSWORD_MISMATCH(1005, "error.password-mismatch", HttpStatus.BAD_REQUEST),
    WRONG_PASSWORD(1006, "Current password is incorrect", HttpStatus.BAD_REQUEST),
    ROLE_NOT_FOUND(1007, "error.role-not-found", HttpStatus.NOT_FOUND),
    TOKEN_GENERATION_FAILED(1008, "Failed to generate access token", HttpStatus.INTERNAL_SERVER_ERROR),
    TOKEN_PARSE_FAILED(1009, "Failed to parse token", HttpStatus.INTERNAL_SERVER_ERROR),
    ACCOUNT_DISABLED(1010, "error.account-disabled", HttpStatus.FORBIDDEN),
    RESET_TOKEN_INVALID(1011, "error.reset-token-invalid", HttpStatus.BAD_REQUEST),
    VERIFY_TOKEN_INVALID(1012, "error.verify-token-invalid", HttpStatus.BAD_REQUEST),
    ACCOUNT_ALREADY_VERIFIED(1013, "error.account-already-verified", HttpStatus.BAD_REQUEST),
    TENANT_NOT_FOUND(1014, "error.tenant-not-found", HttpStatus.NOT_FOUND),
    QUOTA_BELOW_USED(1015, "error.quota-below-used", HttpStatus.BAD_REQUEST),
    INVALID_CURRENT_PASSWORD(1016, "error.invalid-current-password", HttpStatus.UNAUTHORIZED),
    ROLE_ESCALATION_FORBIDDEN(1017, "error.role-escalation-forbidden", HttpStatus.FORBIDDEN),
    SELF_DEACTIVATION_FORBIDDEN(1018, "error.self-deactivation-forbidden", HttpStatus.FORBIDDEN),
    SLUG_ALREADY_EXISTS(1019, "error.slug-already-exists", HttpStatus.CONFLICT),
    SYSTEM_TENANT_CANNOT_BE_DELETED(1020, "error.system-tenant-cannot-be-deleted", HttpStatus.CONFLICT),
    FILE_NOT_FOUND(1021, "error.file-not-found", HttpStatus.NOT_FOUND),
    FOLDER_NOT_FOUND(1022, "error.folder-not-found", HttpStatus.NOT_FOUND),
    FILE_STATUS_INVALID(1023, "error.file-status-invalid", HttpStatus.CONFLICT),
    FILE_TOO_LARGE(1024, "error.file-too-large", HttpStatus.BAD_REQUEST),
    TENANT_QUOTA_EXCEEDED(1025, "error.tenant-quota-exceeded", HttpStatus.CONFLICT),
    USER_QUOTA_EXCEEDED(1026, "error.user-quota-exceeded", HttpStatus.CONFLICT),
    INTERNAL_SERVER_ERROR(1500, "error.internal-server-error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String key;
    private final HttpStatus httpStatus;
}
