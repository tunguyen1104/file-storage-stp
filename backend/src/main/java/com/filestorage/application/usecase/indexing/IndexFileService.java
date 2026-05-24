package com.filestorage.application.usecase.indexing;

import com.filestorage.application.usecase.rag.RagIndexRequestService;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.port.out.ContentExtractionPort;
import com.filestorage.domain.port.out.FileIndexData;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.domain.port.out.SearchCachePort;
import com.filestorage.domain.port.out.SearchIndexPort;
import com.filestorage.infrastructure.config.IndexingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the full index pipeline for a single file. Splits the work
 * across two short transactions plus a heavy I/O phase outside both, so we
 * never hold a DB connection while Tika is parsing or MinIO is uploading.
 *
 * <p>Transactional boundaries live in {@link IndexFileTxHelper} — kept on a
 * separate bean so Spring's AOP proxy actually applies (self-invocation
 * skips the proxy). Same pattern as {@code FailedUploadPurger}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IndexFileService {

    private final IndexFileTxHelper tx;
    private final ObjectStoragePort objectStoragePort;
    private final ContentExtractionPort contentExtractionPort;
    private final SearchIndexPort searchIndexPort;
    private final SearchCachePort searchCachePort;
    private final IndexingPolicy indexingPolicy;
    private final IndexingProperties indexingProperties;
    private final RagIndexRequestService ragIndexRequestService;

    /**
     * @param fileId target file. The file may have been deleted or transitioned
     *               to a non-ACTIVE state since the event was published; we
     *               handle both cases by short-circuiting without touching ES.
     */
    public void execute(UUID fileId) {
        Optional<IndexFileTxHelper.Snapshot> maybe = tx.loadActiveSnapshot(fileId);
        if (maybe.isEmpty()) {
            log.warn("IndexFileService: file {} not found, skipping", fileId);
            return;
        }
        IndexFileTxHelper.Snapshot snap = maybe.get();
        if (snap.status() != FileStatus.ACTIVE) {
            log.warn("IndexFileService: file {} not ACTIVE (status={}), skipping", fileId, snap.status());
            return;
        }

        String extractedContent = null;
        String contentKey = null;

        try {
            if (indexingPolicy.shouldExtractContent(snap.mimeType())) {
                byte[] bytes = objectStoragePort.download(snap.storageKey());
                extractedContent = contentExtractionPort.extractText(
                        new ByteArrayInputStream(bytes),
                        snap.mimeType(),
                        indexingProperties.getMaxContentChars()
                );
                if (extractedContent != null && !extractedContent.isBlank()) {
                    contentKey = buildContentKey(snap.tenantId(), snap.uploadedBy(), snap.id());
                    objectStoragePort.upload(
                            contentKey,
                            extractedContent.getBytes(StandardCharsets.UTF_8),
                            "text/plain; charset=utf-8");
                }
            }

            searchIndexPort.indexFile(new FileIndexData(
                    snap.id(),
                    snap.tenantId(),
                    snap.folderId(),
                    snap.uploadedBy(),
                    snap.originalName(),
                    snap.mimeType(),
                    extractedContent,
                    snap.createdAt()
            ));

            tx.markIndexed(fileId, contentKey);
            requestRagIndex(fileId, snap, contentKey);
            searchCachePort.evictForUser(snap.tenantId(), snap.uploadedBy());

            log.info("IndexFileService: indexed fileId={} contentExtracted={}", fileId, extractedContent != null);
        } catch (RuntimeException e) {
            log.warn("IndexFileService: indexing failed for fileId={}: {}", fileId, e.getMessage());
            try {
                tx.bumpRetryCount(fileId);
            } catch (Exception bumpFailure) {
                log.error("IndexFileService: failed to bump retry counter for fileId={}: {}", fileId, bumpFailure.getMessage());
            }
            throw e;
        }
    }

    private String buildContentKey(UUID tenantId, UUID userId, UUID fileId) {
        return indexingProperties.getContentKeyPrefix() + tenantId + "/" + userId + "/" + fileId + ".txt";
    }

    private void requestRagIndex(UUID fileId, IndexFileTxHelper.Snapshot snap, String contentKey) {
        try {
            ragIndexRequestService.requestIndex(fileId, snap.tenantId(), snap.uploadedBy(), contentKey);
        } catch (Exception e) {
            log.warn("IndexFileService: failed to queue RAG indexing for fileId={}: {}", fileId, e.getMessage());
        }
    }
}
