package com.filestorage.domain.model.search;

import java.util.UUID;

/**
 * Lightweight per-hit projection for the SAYT dropdown. No score, no highlights —
 * keeps the payload tiny so the dropdown stays responsive.
 *
 * <p>{@code folderPath} is shown beneath the file name so the user can tell apart
 * two files with identical names in different folders.
 */
public record SuggestHitView(
        UUID id,
        SearchType type,
        String name,
        String mimeType,
        UUID folderId,
        String folderPath
) {
}
