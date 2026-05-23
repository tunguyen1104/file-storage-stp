package com.filestorage.domain.model.audit;

public enum AuditAction {
    LOGIN_SUCCESS(AuditSeverity.SUCCESS),
    LOGIN_FAILED(AuditSeverity.ERROR),
    LOGOUT(AuditSeverity.INFO),
    PASSWORD_CHANGED(AuditSeverity.WARNING),
    FILE_UPLOAD(AuditSeverity.SUCCESS),
    FILE_DOWNLOAD(AuditSeverity.INFO),
    FILE_DELETE(AuditSeverity.WARNING),
    FILE_RESTORE(AuditSeverity.SUCCESS),
    FILE_PURGED(AuditSeverity.WARNING),
    FOLDER_CREATE(AuditSeverity.SUCCESS),
    FOLDER_DELETE(AuditSeverity.WARNING),
    TENANT_CREATED(AuditSeverity.SUCCESS),
    TENANT_DEACTIVATED(AuditSeverity.WARNING),
    USER_CREATED(AuditSeverity.SUCCESS),
    USER_DEACTIVATED(AuditSeverity.WARNING),
    USER_ROLE_CHANGED(AuditSeverity.WARNING),
    QUOTA_EXCEEDED(AuditSeverity.ERROR),
    UPLOAD_FAILED(AuditSeverity.ERROR),
    VIRUS_DETECTED(AuditSeverity.ERROR);

    private final AuditSeverity severity;

    AuditAction(AuditSeverity severity) {
        this.severity = severity;
    }

    public AuditSeverity severity() {
        return severity;
    }
}
