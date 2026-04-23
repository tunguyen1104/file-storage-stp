package com.filestorage.domain.model.enums;

public enum FileSortBy {
    CREATED_AT("createdAt"),
    ORIGINAL_NAME("originalName"),
    SIZE_BYTES("sizeBytes");

    private final String property;

    FileSortBy(String property) {
        this.property = property;
    }

    public String property() {
        return property;
    }
}

