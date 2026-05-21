package com.filestorage.application.usecase.search;

import com.filestorage.domain.model.search.SearchHitView;
import com.filestorage.domain.model.search.SearchType;
import com.filestorage.domain.model.search.SearchView;
import com.filestorage.domain.model.search.SuggestHitView;
import com.filestorage.domain.model.search.SuggestView;
import com.filestorage.domain.port.out.FolderPathPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Post-processes ES hits to populate the {@code folderPath} field. Runs once
 * per search request — a single recursive CTE resolves the ancestor chains
 * for every distinct folderId across all hits.
 *
 * <p>For a file hit, the containing folder is {@code hit.folderId}. For a
 * folder hit, the containing folder is the folder's own {@code folderId}
 * (which is the parentId in our ES doc model) — see {@link SearchHitView}
 * note where {@code folderId} for a folder hit really means "the folder
 * containing that folder".
 *
 * <p>Root-level items get {@code folderPath = null}.
 */
@Component
@RequiredArgsConstructor
public class FolderPathResolver {

    private static final String SEPARATOR = " / ";

    private final FolderPathPort folderPathPort;

    public SearchView enrich(SearchView view) {
        if (view == null || view.items() == null || view.items().isEmpty()) {
            return view;
        }
        Set<UUID> ids = collectFolderIds(view.items());
        if (ids.isEmpty()) {
            return view;
        }
        Map<UUID, List<String>> chains = folderPathPort.findAncestorChains(ids);
        List<SearchHitView> enriched = new ArrayList<>(view.items().size());
        for (SearchHitView hit : view.items()) {
            String path = pathFor(hit.folderId(), chains);
            enriched.add(new SearchHitView(
                    hit.id(),
                    hit.type(),
                    hit.tenantId(),
                    hit.name(),
                    hit.mimeType(),
                    hit.folderId(),
                    path,
                    hit.createdAt(),
                    hit.score(),
                    hit.highlights()
            ));
        }
        return new SearchView(enriched, view.totalHits(), view.page(), view.size(), view.aggregations());
    }

    public SuggestView enrich(SuggestView view) {
        if (view == null || view.items() == null || view.items().isEmpty()) {
            return view;
        }
        Set<UUID> ids = collectSuggestFolderIds(view.items());
        if (ids.isEmpty()) {
            return view;
        }
        Map<UUID, List<String>> chains = folderPathPort.findAncestorChains(ids);
        List<SuggestHitView> enriched = new ArrayList<>(view.items().size());
        for (SuggestHitView hit : view.items()) {
            String path = pathFor(hit.folderId(), chains);
            enriched.add(new SuggestHitView(
                    hit.id(),
                    hit.type(),
                    hit.name(),
                    hit.mimeType(),
                    hit.folderId(),
                    path
            ));
        }
        return new SuggestView(enriched, view.totalHits());
    }

    // For a file hit, the containing folder is hit.folderId. For a folder hit, the
    // ES "folderId" field already holds the parentId (we map parentId → folderId
    // when building SearchHitView). Either way, the field used here is consistent.
    private Set<UUID> collectFolderIds(List<SearchHitView> items) {
        Set<UUID> ids = new HashSet<>();
        for (SearchHitView h : items) {
            if (h.folderId() != null) {
                ids.add(h.folderId());
            }
        }
        return ids;
    }

    private Set<UUID> collectSuggestFolderIds(List<SuggestHitView> items) {
        Set<UUID> ids = new HashSet<>();
        for (SuggestHitView h : items) {
            if (h.folderId() != null) {
                ids.add(h.folderId());
            }
        }
        return ids;
    }

    private String pathFor(UUID folderId, Map<UUID, List<String>> chains) {
        if (folderId == null) {
            return null;
        }
        List<String> chain = chains.getOrDefault(folderId, Collections.emptyList());
        if (chain.isEmpty()) {
            return null;
        }
        return String.join(SEPARATOR, chain);
    }
}
