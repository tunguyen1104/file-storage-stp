package com.filestorage.application.usecase.indexing;

import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.application.usecase.rag.RagLifecycleSyncService;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.domain.port.out.SearchCachePort;
import com.filestorage.domain.port.out.SearchIndexPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Handles {@code FileIndexEvent(DELETE)} — physical removal of a file.
 *
 * <p>Removes the ES doc and best-effort deletes the extracted text from MinIO.
 * {@code userId} (the file owner) is carried in the event so we avoid a
 * DB round-trip here — the DB row may already be gone at this point anyway.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeleteFileIndexService {

    private final FileJpaRepository fileRepository;
    private final SearchIndexPort searchIndexPort;
    private final SearchCachePort searchCachePort;
    private final ObjectStoragePort objectStoragePort;
    private final RagLifecycleSyncService ragLifecycleSyncService;

    public void execute(UUID fileId, UUID tenantId, UUID userId) {
        String contentKey = lookupContentKey(fileId);
        searchIndexPort.deleteFile(fileId);
        if (contentKey != null) {
            try {
                objectStoragePort.deleteObjectIfExists(contentKey);
            } catch (Exception e) {
                log.warn("DeleteFileIndexService: failed to delete extracted text key={}: {}", contentKey, e.getMessage());
            }
        }
        try {
            ragLifecycleSyncService.onFileHardDeleted(fileId);
        } catch (Exception e) {
            log.warn("DeleteFileIndexService: failed to sync RAG hard delete fileId={}: {}", fileId, e.getMessage());
        }
        searchCachePort.evictForUser(tenantId, userId);
        log.info("DeleteFileIndexService: deleted ES doc for fileId={}", fileId);
    }

    private String lookupContentKey(UUID fileId) {
        return fileRepository.findById(fileId).map(f -> f.getContentKey()).orElse(null);
    }
}
