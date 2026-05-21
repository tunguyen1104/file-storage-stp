package com.filestorage.domain.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outbound port for resolving folder ancestor chains. One implementation uses a
 * single recursive CTE so the whole batch resolves in one DB round-trip.
 *
 * <p>The returned list for each {@code folderId} is the ordered chain
 * {@code [root, ..., immediateParent, folder]} (ascending depth). Folders that
 * are themselves at the root return a one-element list.
 */
public interface FolderPathPort {

    /**
     * For each folderId, returns its ancestor chain (root-first, inclusive of
     * the folder itself). IDs absent from the input are not present in the
     * result map. Empty input returns an empty map.
     */
    Map<UUID, List<String>> findAncestorChains(Collection<UUID> folderIds);
}
