package com.filestorage.adapter.out.persistence;

import com.filestorage.domain.port.out.FolderPathPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves folder ancestor chains in a single recursive CTE.
 *
 * <p>The CTE walks {@code parent_id} upward from each input id, yielding every
 * ancestor row (id, parent_id, name). We then build a child→parent map in Java
 * and walk it back down from each input to produce the ordered chain
 * {@code [root, ..., immediateParent, folder]}. One DB round-trip total —
 * whether the caller passes 1 id or 200, and whether the tree is 2 deep or
 * 20 deep.
 */
@Component
@RequiredArgsConstructor
public class FolderPathAdapter implements FolderPathPort {

    @PersistenceContext
    private EntityManager entityManager;

    private static final String IDS_PARAM = "ids";

    @Override
    @SuppressWarnings("unchecked")
    public Map<UUID, List<String>> findAncestorChains(Collection<UUID> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // De-duplicate to keep the IN list small even when many hits live in the same folder.
        Collection<UUID> distinct = new HashSet<>(folderIds);

        String sql = """
                WITH RECURSIVE ancestors AS (
                    SELECT id, parent_id, name FROM folders WHERE id IN (:ids)
                    UNION ALL
                    SELECT f.id, f.parent_id, f.name
                    FROM folders f
                    JOIN ancestors a ON f.id = a.parent_id
                )
                SELECT id, parent_id, name FROM ancestors
                """;

        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter(IDS_PARAM, distinct)
                .getResultList();

        // Build id → (parentId, name) lookup. The same row may appear once per
        // descendant that has it as an ancestor — last write wins, all values
        // are identical so this is safe.
        Map<UUID, NodeRow> nodes = new HashMap<>(rows.size());
        for (Object[] row : rows) {
            UUID id = toUuid(row[0]);
            UUID parentId = toUuid(row[1]);
            String name = (String) row[2];
            nodes.put(id, new NodeRow(parentId, name));
        }

        Map<UUID, List<String>> result = new HashMap<>(distinct.size());
        for (UUID folderId : distinct) {
            if (!nodes.containsKey(folderId)) {
                continue;
            }
            LinkedList<String> chain = new LinkedList<>();
            UUID cursor = folderId;
            // Hard cap on depth to defend against accidental cycles in legacy data.
            for (int depth = 0; depth < 1000 && cursor != null; depth++) {
                NodeRow node = nodes.get(cursor);
                if (node == null) {
                    break;
                }
                chain.addFirst(node.name);
                cursor = node.parentId;
            }
            result.put(folderId, new ArrayList<>(chain));
        }
        return result;
    }

    private static UUID toUuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

    private record NodeRow(UUID parentId, String name) {}
}
