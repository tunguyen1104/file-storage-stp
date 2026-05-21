package com.filestorage.application.usecase.search;

import com.filestorage.adapter.out.persistence.repository.FileShareJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.search.SearchView;
import com.filestorage.domain.port.in.search.SearchQuery;
import com.filestorage.domain.port.in.search.SearchUseCase;
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
 * Fuzzy full-text search (the Enter-key path).
 *
 * <p>Flow: cache lookup → fetch shared ids (MEMBER only) → ES → resolve folder
 * paths → cache put.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SearchService implements SearchUseCase {

    private final SearchIndexPort searchIndexPort;
    private final SearchCachePort searchCachePort;
    private final FolderPathResolver folderPathResolver;
    private final FileShareJpaRepository fileShareRepository;
    private final SearchProperties searchProperties;

    @Override
    public SearchView execute(SearchQuery query) {
        validate(query);

        // SUPER_ADMIN has cross-tenant visibility — cache invalidation would require
        // a system-wide evict on every file change. Skip caching entirely.
        if (query.role() == RoleCode.SUPER_ADMIN) {
            SearchView raw = searchIndexPort.searchAll(query, Collections.emptyList(), Collections.emptyList());
            return folderPathResolver.enrich(raw);
        }

        boolean isMember = query.role() == RoleCode.MEMBER;
        UUID cacheUserId = isMember ? query.userId() : null;
        String cacheKey = "search:" + query.tenantId() + ":" + (isMember ? "member:" + query.userId() : "tenant_admin") + ":" + SearchQueryHasher.hash(query);

        Optional<SearchView> cached = searchCachePort.get(cacheKey, SearchView.class);
        if (cached.isPresent()) {
            log.debug("Search cache hit for tenant={}", query.tenantId());
            return cached.get();
        }

        List<UUID> sharedFileIds = Collections.emptyList();
        List<UUID> sharedFolderIds = Collections.emptyList();
        if (isMember) {
            Instant now = Instant.now();
            sharedFileIds = fileShareRepository.findAccessibleFileIdsForUser(
                    query.userId(), query.tenantId(), now);
            sharedFolderIds = fileShareRepository.findAccessibleFolderIdClosureForUser(
                    query.userId(), query.tenantId(), now);
        }

        SearchView raw = searchIndexPort.searchAll(query, sharedFileIds, sharedFolderIds);
        SearchView enriched = folderPathResolver.enrich(raw);

        searchCachePort.put(
                cacheKey,
                enriched,
                Duration.ofMinutes(searchProperties.getCache().getTtlMinutes()),
                query.tenantId(),
                cacheUserId
        );
        return enriched;
    }

    private void validate(SearchQuery query) {
        if (query.q() == null || query.q().isBlank()) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
    }
}
