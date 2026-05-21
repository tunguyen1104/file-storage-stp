package com.filestorage.domain.model.enums;

public enum ShareSortBy {
    CREATED_AT("createdAt"),
    EXPIRES_AT("expiresAt"),
    REVOKED_AT("revokedAt"),
    STATUS("status"),
    TENANT_ID("tenantId");

    private final String property;

    ShareSortBy(String property) {
        this.property = property;
    }

    public String property() {
        return property;
    }
}
