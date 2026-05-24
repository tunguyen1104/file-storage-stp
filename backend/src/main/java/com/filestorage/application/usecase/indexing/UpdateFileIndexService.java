package com.filestorage.application.usecase.indexing;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.application.usecase.rag.RagLifecycleSyncService;
import com.filestorage.domain.port.out.SearchCachePort;
import com.filestorage.domain.port.out.SearchIndexPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles {@code FileIndexEvent(UPSERT_METADATA)} — fired by rename and move.
 *
 * <p>Reloads {@code originalName} and {@code folderId} fresh from the DB and
 * partial-updates the ES doc. Loading both fields unconditionally is
 * deliberate: the event carries only a fileId, and reusing one consumer for
 * rename + move avoids a redundant consumer class.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UpdateFileIndexService {

    private final Loader loader;
    private final SearchIndexPort searchIndexPort;
    private final SearchCachePort searchCachePort;
    private final RagLifecycleSyncService ragLifecycleSyncService;

    public void execute(UUID fileId, UUID userId) {
        Loader.Snapshot snapshot = loader.loadSnapshot(fileId);
        if (snapshot == null) {
            log.warn("UpdateFileIndexService: file {} not found, skipping", fileId);
            return;
        }
        searchIndexPort.updateFileMetadata(snapshot.id(), snapshot.originalName(), snapshot.folderId());
        try {
            ragLifecycleSyncService.onFileMetadataUpdated(snapshot.id(), snapshot.originalName(), snapshot.folderId());
        } catch (Exception e) {
            log.warn("UpdateFileIndexService: failed to sync RAG metadata fileId={}: {}", fileId, e.getMessage());
        }
        searchCachePort.evictForUser(snapshot.tenantId(), userId);
        log.info("UpdateFileIndexService: updated metadata for fileId={}", fileId);
    }

    /**
     * Separate bean so {@link Transactional} survives self-invocation rules.
     */
    @Component
    @RequiredArgsConstructor
    public static class Loader {

        private final FileJpaRepository fileRepository;

        @Transactional(readOnly = true)
        public Snapshot loadSnapshot(UUID fileId) {
            File file = fileRepository.findById(fileId).orElse(null);
            if (file == null) {
                return null;
            }
            return new Snapshot(
                    file.getId(),
                    file.getTenant().getId(),
                    file.getFolder() == null ? null : file.getFolder().getId(),
                    file.getOriginalName()
            );
        }

        public record Snapshot(UUID id, UUID tenantId, UUID folderId, String originalName) {}
    }
}
