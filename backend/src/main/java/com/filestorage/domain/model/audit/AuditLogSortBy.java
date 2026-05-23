package com.filestorage.domain.model.audit;

public enum AuditLogSortBy {
    CREATED_AT("createdAt"),
    ACTION("action");

    private final String property;

    AuditLogSortBy(String property) {
        this.property = property;
    }

    public String property() {
        return property;
    }
}
