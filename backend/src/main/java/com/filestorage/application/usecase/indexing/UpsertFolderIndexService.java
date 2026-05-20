package com.filestorage.application.usecase.indexing;

import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.domain.port.out.FolderIndexData;
import com.filestorage.domain.port.out.SearchCachePort;
import com.filestorage.domain.port.out.SearchIndexPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles {@code FolderIndexEvent(UPSERT)} — fired on create, rename, and move.
 *
 * <p>Loads the current folder state and indexes it. UPSERT semantics on
 * {@link SearchIndexPort#indexFolder} means create-vs-update is the adapter's
 * concern, not ours.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UpsertFolderIndexService {

    private final Loader loader;
    private final SearchIndexPort searchIndexPort;
    private final SearchCachePort searchCachePort;

    public void execute(UUID folderId) {
        FolderIndexData data = loader.loadSnapshot(folderId);
        if (data == null) {
            log.warn("UpsertFolderIndexService: folder {} not found, skipping", folderId);
            return;
        }
        searchIndexPort.indexFolder(data);
        searchCachePort.evictForUser(data.tenantId(), data.createdBy());
        log.info("UpsertFolderIndexService: indexed folderId={}", folderId);
    }

    /** Tx boundary in a dedicated bean — see {@code IndexFileTxHelper} for the same pattern. */
    @Component
    @RequiredArgsConstructor
    public static class Loader {

        private final FolderJpaRepository folderRepository;

        @Transactional(readOnly = true)
        public FolderIndexData loadSnapshot(UUID folderId) {
            Folder folder = folderRepository.findById(folderId).orElse(null);
            if (folder == null) {
                return null;
            }
            return new FolderIndexData(
                    folder.getId(),
                    folder.getTenant().getId(),
                    folder.getParent() == null ? null : folder.getParent().getId(),
                    folder.getCreatedBy().getId(),
                    folder.getName(),
                    folder.getCreatedAt()
            );
        }
    }
}
