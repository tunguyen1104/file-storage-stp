package com.filestorage.domain.model.search;

/**
 * What is being searched. Drives both query routing (single index vs.
 * parallel files+folders) and response shape.
 */
public enum SearchType {
    ALL,
    FILE,
    FOLDER
}
