package com.filestorage.domain.model.enums;

public enum FolderSortBy {
    NAME("name"),
    CREATED_AT("createdAt"),
    SIZE_BYTES("sizeBytes");

    private final String property;

    FolderSortBy(String property) {
        this.property = property;
    }

    public String property() {
        return property;
    }
}

