package com.filestorage.application.usecase.rag;

import com.filestorage.adapter.out.persistence.entity.FileRagDocument;
import com.filestorage.adapter.out.persistence.repository.FileRagDocumentJpaRepository;
import com.filestorage.domain.model.enums.RagDocumentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RagFileTxHelper {

    private static final int MAX_ERROR_LENGTH = 4000;

    private final FileRagDocumentJpaRepository ragDocumentRepository;

    @Transactional(readOnly = true)
    public Optional<FileRagDocument> find(UUID fileId) {
        return ragDocumentRepository.findById(fileId);
    }

    @Transactional(readOnly = true)
    public boolean canRestoreExistingChunks(UUID fileId) {
        return ragDocumentRepository.findById(fileId)
                .map(document -> (document.getStatus() == RagDocumentStatus.PENDING_DELETE
                        || document.getStatus() == RagDocumentStatus.INDEXED)
                        && document.getChunkCount() > 0
                        && document.getActiveIngestionId() != null)
                .orElse(false);
    }

    @Transactional
    public void markPending(
            UUID fileId,
            String sourceKey,
            String embeddingModel,
            int embeddingDimensions,
            String chunkerVersion
    ) {
        FileRagDocument document = loadOrCreateForUpdate(fileId);
        document.setStatus(RagDocumentStatus.PENDING);
        document.setSourceKey(sourceKey);
        document.setSourceType("EXTRACTED_TEXT");
        document.setChunkCount(0);
        document.setEmbeddingModel(embeddingModel);
        document.setEmbeddingDimensions(embeddingDimensions);
        document.setChunkerVersion(chunkerVersion);
        document.setActiveIngestionId(null);
        document.setLastError(null);
        document.setIndexedAt(null);
        ragDocumentRepository.save(document);
    }

    @Transactional
    public UUID markIndexing(
            UUID fileId,
            String sourceKey,
            String embeddingModel,
            int embeddingDimensions,
            String chunkerVersion
    ) {
        UUID ingestionId = UUID.randomUUID();
        FileRagDocument document = loadOrCreateForUpdate(fileId);
        document.setStatus(RagDocumentStatus.INDEXING);
        document.setSourceKey(sourceKey);
        document.setSourceType("EXTRACTED_TEXT");
        document.setChunkCount(0);
        document.setEmbeddingModel(embeddingModel);
        document.setEmbeddingDimensions(embeddingDimensions);
        document.setChunkerVersion(chunkerVersion);
        document.setActiveIngestionId(ingestionId);
        document.setLastError(null);
        document.setIndexedAt(null);
        ragDocumentRepository.save(document);
        return ingestionId;
    }

    @Transactional
    public void markIndexed(UUID fileId, UUID ingestionId, int chunkCount) {
        FileRagDocument document = ragDocumentRepository.findByFileIdForUpdate(fileId)
                .orElseThrow(() -> new IllegalStateException("RAG document row not found for file " + fileId));
        requireCurrentIngestion(document, ingestionId);
        document.setStatus(RagDocumentStatus.INDEXED);
        document.setChunkCount(chunkCount);
        document.setRetryCount(0);
        document.setLastError(null);
        document.setIndexedAt(Instant.now());
        ragDocumentRepository.save(document);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID fileId, UUID ingestionId, String error) {
        FileRagDocument document = loadOrCreateForUpdate(fileId);
        if (document.getActiveIngestionId() != null && !document.getActiveIngestionId().equals(ingestionId)) {
            return;
        }
        document.setStatus(RagDocumentStatus.FAILED);
        document.setRetryCount(document.getRetryCount() + 1);
        document.setLastError(truncate(error));
        document.setIndexedAt(null);
        ragDocumentRepository.save(document);
    }

    @Transactional
    public void markUnsupported(UUID fileId, String sourceKey, String reason) {
        FileRagDocument document = loadOrCreateForUpdate(fileId);
        document.setStatus(RagDocumentStatus.UNSUPPORTED);
        document.setSourceKey(sourceKey);
        document.setSourceType("EXTRACTED_TEXT");
        document.setChunkCount(0);
        document.setActiveIngestionId(null);
        document.setLastError(truncate(reason));
        document.setIndexedAt(null);
        ragDocumentRepository.save(document);
    }

    @Transactional
    public void markPendingDelete(UUID fileId) {
        ragDocumentRepository.findByFileIdForUpdate(fileId).ifPresent(document -> {
            document.setStatus(RagDocumentStatus.PENDING_DELETE);
            document.setLastError(null);
            ragDocumentRepository.save(document);
        });
    }

    @Transactional
    public void markRestored(UUID fileId) {
        ragDocumentRepository.findByFileIdForUpdate(fileId).ifPresent(document -> {
            document.setStatus(RagDocumentStatus.INDEXED);
            document.setLastError(null);
            ragDocumentRepository.save(document);
        });
    }

    @Transactional
    public void markDeleted(UUID fileId) {
        ragDocumentRepository.findByFileIdForUpdate(fileId).ifPresent(document -> {
            document.setStatus(RagDocumentStatus.DELETED);
            document.setActiveIngestionId(null);
            document.setChunkCount(0);
            document.setLastError(null);
            document.setIndexedAt(null);
            ragDocumentRepository.save(document);
        });
    }

    private FileRagDocument loadOrCreateForUpdate(UUID fileId) {
        return ragDocumentRepository.findByFileIdForUpdate(fileId)
                .orElseGet(() -> FileRagDocument.builder()
                        .fileId(fileId)
                        .status(RagDocumentStatus.PENDING)
                        .sourceType("EXTRACTED_TEXT")
                        .build());
    }

    private static void requireCurrentIngestion(FileRagDocument document, UUID ingestionId) {
        if (!ingestionId.equals(document.getActiveIngestionId())) {
            throw new IllegalStateException("Stale RAG ingestion for file " + document.getFileId());
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
