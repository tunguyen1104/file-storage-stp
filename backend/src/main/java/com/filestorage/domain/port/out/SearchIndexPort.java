package com.filestorage.domain.port.out;

import com.filestorage.domain.model.search.SearchView;
import com.filestorage.domain.model.search.SuggestView;
import com.filestorage.domain.port.in.search.SearchQuery;
import com.filestorage.domain.port.in.search.SuggestQuery;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Outbound port abstracting the search backend (Elasticsearch in the current
 * deployment). The adapter handles all index-specific concerns: document
 * shape, analyzer choice, visibility filter, and pagination over-fetch.
 *
 * <p>Read paths receive {@code sharedFileIds} / {@code sharedFolderIds}
 * pre-fetched by the application layer — the port itself does not hit the DB.
 * Both lists are empty (never {@code null}) for {@code TENANT_ADMIN} and
 * {@code SUPER_ADMIN} since their visibility rules do not depend on shares.
 */
public interface SearchIndexPort {

    /** Full (re)index of a file: writes name, metadata, and optional content. */
    void indexFile(FileIndexData data);

    /**
     * Partial update of a file's name and folderId without touching content.
     * Used for rename and move events.
     */
    void updateFileMetadata(UUID fileId, String originalName, UUID folderId);

    /** Removes a file's ES document. */
    void deleteFile(UUID fileId);

    /** Indexes a folder document; idempotent (UPSERT semantics). */
    void indexFolder(FolderIndexData data);

    /** Removes a folder's ES document. */
    void deleteFolder(UUID folderId);

    /**
     * Fuzzy full-text search across files and/or folders honouring the role and
     * shared-resource access. Returns hits with highlights, pagination metadata,
     * and aggregations. {@code folderPath} on each hit is left {@code null} —
     * the application layer fills it via {@code FolderPathPort}.
     */
    SearchView searchAll(SearchQuery query,
                         List<UUID> sharedFileIds,
                         List<UUID> sharedFolderIds);

    /**
     * SAYT lookup using {@code bool_prefix} over {@code search_as_you_type}
     * sub-fields. Capped at {@link SuggestQuery#size()}.
     */
    SuggestView suggest(SuggestQuery query,
                        Collection<UUID> sharedFileIds,
                        Collection<UUID> sharedFolderIds);
}
