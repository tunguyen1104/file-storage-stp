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
    FOLDER_NAME_CONFLICT(1027, "error.folder-name-conflict", HttpStatus.CONFLICT),
    FOLDER_NOT_EMPTY(1028, "error.folder-not-empty", HttpStatus.CONFLICT),
    FOLDER_INVALID_MOVE(1029, "error.folder-invalid-move", HttpStatus.CONFLICT),
    QUOTA_EXCEEDED(1030, "error.quota-exceeded", HttpStatus.UNPROCESSABLE_CONTENT),
    INVALID_FILE_STATUS(1031, "error.invalid-file-status", HttpStatus.CONFLICT),
    FILE_NOT_FOUND_ON_STORAGE(1032, "error.file-not-found-on-storage", HttpStatus.UNPROCESSABLE_CONTENT),
    FILE_METADATA_MISMATCH(1033, "error.file-metadata-mismatch", HttpStatus.UNPROCESSABLE_CONTENT),
    UPLOAD_SESSION_NOT_FOUND(1034, "error.upload-session-not-found", HttpStatus.NOT_FOUND),
    INVALID_SESSION_STATUS(1035, "error.invalid-session-status", HttpStatus.CONFLICT),
    UPLOAD_SESSION_EXPIRED(1036, "error.upload-session-expired", HttpStatus.UNPROCESSABLE_CONTENT),
    PARTS_INCOMPLETE(1037, "error.parts-incomplete", HttpStatus.UNPROCESSABLE_CONTENT),
    UNSUPPORTED_FILE_TYPE(1038, "error.file.unsupported_type", HttpStatus.UNPROCESSABLE_CONTENT),
    INVALID_PART_NUMBER(1039, "error.invalid-part-number", HttpStatus.BAD_REQUEST),
    PART_ALREADY_COMPLETED(1040, "error.part-already-completed", HttpStatus.CONFLICT),
    EXPIRES_AT_REQUIRED(1041, "error.share.expires-at-required", HttpStatus.BAD_REQUEST),
    EXPIRES_AT_IN_PAST(1042, "error.share.expires-at-in-past", HttpStatus.BAD_REQUEST),
    FORBIDDEN_NOT_RESOURCE_OWNER(1043, "error.share.forbidden-not-owner", HttpStatus.FORBIDDEN),
    SELF_SHARE_NOT_ALLOWED(1044, "error.share.self-share-not-allowed", HttpStatus.FORBIDDEN),
    CROSS_TENANT_SHARE_NOT_ALLOWED(1045, "error.share.cross-tenant", HttpStatus.FORBIDDEN),
    SHARE_ALREADY_EXISTS(1046, "error.share.already-exists", HttpStatus.CONFLICT),
    SHARE_NOT_FOUND(1047, "error.share.not-found", HttpStatus.NOT_FOUND),
    SHARE_REVOKED_RESHARE_REQUIRED(1048, "error.share.revoked-reshare-required", HttpStatus.CONFLICT),
    FORBIDDEN_ADMIN_REQUIRED(1049, "error.share.admin-required", HttpStatus.FORBIDDEN),
    SHARED_BY_OUT_OF_TENANT(1050, "error.share.shared-by-out-of-tenant", HttpStatus.FORBIDDEN),
    SSE_CONNECTION_LIMIT_EXCEEDED(1051, "error.sse-connection-limit-exceeded", HttpStatus.TOO_MANY_REQUESTS),
    TENANT_ADMIN_ROLE_DEMOTION_FORBIDDEN(1052, "error.tenant-admin-role-demotion-forbidden", HttpStatus.FORBIDDEN),
    TENANT_ADMIN_DEACTIVATION_FORBIDDEN(1053, "error.tenant-admin-deactivation-forbidden", HttpStatus.FORBIDDEN),
    INTERNAL_SERVER_ERROR(1500, "error.internal-server-error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String key;
    private final HttpStatus httpStatus;
}
