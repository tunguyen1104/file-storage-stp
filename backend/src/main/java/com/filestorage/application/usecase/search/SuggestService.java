package com.filestorage.application.usecase.search;

import com.filestorage.adapter.out.persistence.repository.FileShareJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.search.SuggestHitView;
import com.filestorage.domain.model.search.SuggestView;
import com.filestorage.domain.port.in.search.SuggestQuery;
import com.filestorage.domain.port.in.search.SuggestUseCase;
import com.filestorage.domain.port.out.SearchCachePort;
import com.filestorage.domain.port.out.SearchIndexPort;
import com.filestorage.infrastructure.config.SearchProperties;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SAYT — the autocomplete dropdown path.
 *
 * <p>{@code size} is intentionally excluded from the cache hash: we always
 * query ES with the configured maxSize so the cache stores the largest
 * possible payload, then trim to the caller's requested size at read time.
 * That gives a much higher cache-hit rate (one entry serves any size) at the
 * cost of a few unused list items per request.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SuggestService implements SuggestUseCase {

    private final SearchIndexPort searchIndexPort;
    private final SearchCachePort searchCachePort;
    private final FolderPathResolver folderPathResolver;
    private final FileShareJpaRepository fileShareRepository;
    private final SearchProperties searchProperties;

    @Override
    public SuggestView execute(SuggestQuery query) {
        validate(query);
        int requestedSize = clampSize(query.size());
        int maxSize = searchProperties.getSuggest().getMaxSize();

        // Always query (and cache) at maxSize; trim to requestedSize at the end.
        SuggestQuery normalized = new SuggestQuery(
                query.q().trim(),
                query.userId(),
                query.tenantId(),
                query.role(),
                query.type(),
                maxSize
        );

        // SUPER_ADMIN has cross-tenant visibility — cache invalidation would require
        // a system-wide evict on every file change. Skip caching entirely.
        if (normalized.role() == RoleCode.SUPER_ADMIN) {
            SuggestView raw = searchIndexPort.suggest(normalized, Collections.emptyList(), Collections.emptyList());
            return trim(folderPathResolver.enrich(raw), requestedSize);
        }

        boolean isMember = normalized.role() == RoleCode.MEMBER;
        UUID cacheUserId = isMember ? normalized.userId() : null;
        String cacheKey = "sayt:" + normalized.tenantId() + ":" + (isMember ? "member:" + normalized.userId() : "tenant_admin") + ":" + SearchQueryHasher.hash(normalized);

        Optional<SuggestView> cached = searchCachePort.get(cacheKey, SuggestView.class);
        if (cached.isPresent()) {
            return trim(cached.get(), requestedSize);
        }

        List<UUID> sharedFileIds = Collections.emptyList();
        List<UUID> sharedFolderIds = Collections.emptyList();
        if (isMember) {
            Instant now = Instant.now();
            sharedFileIds = fileShareRepository.findAccessibleFileIdsForUser(
                    normalized.userId(), normalized.tenantId(), now);
            sharedFolderIds = fileShareRepository.findAccessibleFolderIdClosureForUser(
                    normalized.userId(), normalized.tenantId(), now);
        }

        SuggestView raw = searchIndexPort.suggest(normalized, sharedFileIds, sharedFolderIds);
        SuggestView enriched = folderPathResolver.enrich(raw);

        searchCachePort.put(
                cacheKey,
                enriched,
                Duration.ofSeconds(searchProperties.getCache().getSaytTtlSeconds()),
                normalized.tenantId(),
                cacheUserId
        );
        return trim(enriched, requestedSize);
    }

    private void validate(SuggestQuery query) {
        if (query.q() == null) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        String trimmed = query.q().trim();
        int maxLen = searchProperties.getSuggest().getMaxQueryLength();
        if (trimmed.isEmpty() || trimmed.length() > maxLen) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
    }

    private int clampSize(int requested) {
        int defaultSize = searchProperties.getSuggest().getDefaultSize();
        int maxSize = searchProperties.getSuggest().getMaxSize();
        if (requested <= 0) {
            return defaultSize;
        }
        return Math.min(requested, maxSize);
    }

    private static SuggestView trim(SuggestView view, int size) {
        List<SuggestHitView> items = view.items();
        if (items == null || items.size() <= size) {
            return view;
        }
        return new SuggestView(items.subList(0, size), view.totalHits());
    }
}
