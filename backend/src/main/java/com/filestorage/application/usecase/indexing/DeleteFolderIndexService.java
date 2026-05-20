package com.filestorage.application.usecase.indexing;

import com.filestorage.domain.port.out.SearchCachePort;
import com.filestorage.domain.port.out.SearchIndexPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Handles {@code FolderIndexEvent(DELETE)} — physical removal of a folder.
 * Removes the ES doc and flushes the relevant search cache entries.
 *
 * <p>{@code userId} (the folder owner) is carried in the event — no DB round-trip needed.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeleteFolderIndexService {

    private final SearchIndexPort searchIndexPort;
    private final SearchCachePort searchCachePort;

    public void execute(UUID folderId, UUID tenantId, UUID userId) {
        searchIndexPort.deleteFolder(folderId);
        searchCachePort.evictForUser(tenantId, userId);
        log.info("DeleteFolderIndexService: deleted ES doc for folderId={}", folderId);
    }
}
