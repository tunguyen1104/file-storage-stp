package com.filestorage.application.usecase.rag;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.rag.EmbeddingVector;
import com.filestorage.domain.model.rag.RagChunkIndexData;
import com.filestorage.domain.model.rag.RagTextChunk;
import com.filestorage.domain.port.out.EmbeddingPort;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.domain.port.out.RagVectorIndexPort;
import com.filestorage.infrastructure.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class IngestRagFileService {

    private final Loader loader;
    private final ObjectStoragePort objectStoragePort;
    private final PlainTextChunker chunker;
    private final EmbeddingPort embeddingPort;
    private final RagVectorIndexPort ragVectorIndexPort;
    private final RagFileTxHelper tx;
    private final RagProperties ragProperties;

    public void execute(UUID fileId) {
        if (!ragProperties.isEnabled()) {
            log.debug("IngestRagFileService: RAG disabled, skip fileId={}", fileId);
            return;
        }

        Loader.Snapshot snapshot = loader.loadSnapshot(fileId);
        if (snapshot == null) {
            log.warn("IngestRagFileService: file {} not found, skipping", fileId);
            return;
        }
        if (!prepareActiveFile(snapshot)) {
            return;
        }

        UUID ingestionId = tx.markIndexing(
                snapshot.id(),
                snapshot.contentKey(),
                ragProperties.getEmbedding().getModel(),
                ragProperties.getEmbedding().getDimensions(),
                ragProperties.getChunks().getVersion()
        );

        try {
            String text = new String(objectStoragePort.download(snapshot.contentKey()), StandardCharsets.UTF_8);
            List<RagTextChunk> chunks = chunker.chunk(text);
            if (chunks.isEmpty()) {
                markUnsupportedAndHideChunks(snapshot, "No extracted text content");
                return;
            }

            upsertEmbeddedChunks(snapshot, ingestionId, chunks);

            Loader.Snapshot latest = loader.loadSnapshot(fileId);
            if (!ensureStillActive(latest, snapshot, ingestionId)) {
                return;
            }

            ragVectorIndexPort.updateFileMetadata(fileId, latest.originalName(), latest.folderId());
            ragVectorIndexPort.markChunksReadyByFileIdAndIngestionId(fileId, ingestionId);
            ragVectorIndexPort.deleteChunksByFileIdExceptIngestionId(fileId, ingestionId);
            tx.markIndexed(fileId, ingestionId, chunks.size());
            log.info("IngestRagFileService: indexed RAG fileId={} chunks={} ingestionId={}", fileId, chunks.size(), ingestionId);
        } catch (RuntimeException e) {
            cleanupFailedIngestion(fileId, ingestionId, e);
            throw e;
        }
    }

    private boolean prepareActiveFile(Loader.Snapshot snapshot) {
        if (snapshot.status() == FileStatus.PENDING_DELETE) {
            ragVectorIndexPort.updateFileRetrievability(snapshot.id(), false, FileStatus.PENDING_DELETE);
            tx.markPendingDelete(snapshot.id());
            log.info("IngestRagFileService: fileId={} is PENDING_DELETE, hid chunks", snapshot.id());
            return false;
        }
        if (snapshot.status() == FileStatus.DELETED) {
            ragVectorIndexPort.deleteChunksByFileId(snapshot.id());
            tx.markDeleted(snapshot.id());
            log.info("IngestRagFileService: fileId={} is DELETED, removed chunks", snapshot.id());
            return false;
        }
        if (snapshot.status() != FileStatus.ACTIVE) {
            log.info("IngestRagFileService: fileId={} status={} not ACTIVE, skipping", snapshot.id(), snapshot.status());
            return false;
        }
        if (snapshot.contentKey() == null || snapshot.contentKey().isBlank()) {
            markUnsupportedAndHideChunks(snapshot, "No extracted text content");
            return false;
        }
        return true;
    }

    private boolean ensureStillActive(Loader.Snapshot latest, Loader.Snapshot original, UUID ingestionId) {
        if (latest == null) {
            ragVectorIndexPort.deleteChunksByFileIdAndIngestionId(original.id(), ingestionId);
            log.warn("IngestRagFileService: fileId={} disappeared during ingestion, removed staged chunks", original.id());
            return false;
        }
        if (latest.status() == FileStatus.PENDING_DELETE) {
            ragVectorIndexPort.deleteChunksByFileIdAndIngestionId(original.id(), ingestionId);
            ragVectorIndexPort.updateFileRetrievability(original.id(), false, FileStatus.PENDING_DELETE);
            tx.markPendingDelete(original.id());
            log.info("IngestRagFileService: fileId={} became PENDING_DELETE during ingestion", original.id());
            return false;
        }
        if (latest.status() == FileStatus.DELETED) {
            ragVectorIndexPort.deleteChunksByFileId(original.id());
            tx.markDeleted(original.id());
            log.info("IngestRagFileService: fileId={} became DELETED during ingestion", original.id());
            return false;
        }
        if (latest.status() != FileStatus.ACTIVE) {
            ragVectorIndexPort.deleteChunksByFileIdAndIngestionId(original.id(), ingestionId);
            log.info("IngestRagFileService: fileId={} became status={} during ingestion", original.id(), latest.status());
            return false;
        }
        return true;
    }

    private void upsertEmbeddedChunks(Loader.Snapshot snapshot, UUID ingestionId, List<RagTextChunk> chunks) {
        int batchSize = Math.max(1, ragProperties.getEmbedding().getBatchSize());
        for (int from = 0; from < chunks.size(); from += batchSize) {
            int to = Math.min(from + batchSize, chunks.size());
            List<RagTextChunk> batch = chunks.subList(from, to);
            List<EmbeddingVector> embeddings = embeddingPort.embed(batch.stream()
                    .map(RagTextChunk::content)
                    .toList());
            ragVectorIndexPort.upsertChunks(toIndexData(snapshot, ingestionId, batch, embeddings));
        }
    }

    private List<RagChunkIndexData> toIndexData(
            Loader.Snapshot snapshot,
            UUID ingestionId,
            List<RagTextChunk> chunks,
            List<EmbeddingVector> embeddings
    ) {
        if (embeddings.size() != chunks.size()) {
            throw new IllegalStateException("Embedding response size does not match chunk batch size");
        }
        Map<Integer, EmbeddingVector> byBatchIndex = embeddings.stream()
                .collect(Collectors.toMap(EmbeddingVector::index, Function.identity()));

        List<RagChunkIndexData> result = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            RagTextChunk chunk = chunks.get(i);
            EmbeddingVector vector = byBatchIndex.get(i);
            if (vector == null) {
                throw new IllegalStateException("Missing embedding for batch index " + i);
            }
            result.add(new RagChunkIndexData(
                    snapshot.id(),
                    snapshot.tenantId(),
                    snapshot.folderId(),
                    snapshot.uploadedBy(),
                    snapshot.originalName(),
                    chunk.index(),
                    ingestionId,
                    chunk.content(),
                    vector.values(),
                    snapshot.contentKey(),
                    ragProperties.getEmbedding().getModel(),
                    ragProperties.getChunks().getVersion(),
                    false,
                    true,
                    FileStatus.ACTIVE,
                    Instant.now()
            ));
        }
        return result;
    }

    private void markUnsupportedAndHideChunks(Loader.Snapshot snapshot, String reason) {
        ragVectorIndexPort.updateFileRetrievability(snapshot.id(), false, snapshot.status());
        tx.markUnsupported(snapshot.id(), snapshot.contentKey(), reason);
        log.info("IngestRagFileService: marked unsupported fileId={} reason={}", snapshot.id(), reason);
    }

    private void cleanupFailedIngestion(UUID fileId, UUID ingestionId, RuntimeException e) {
        try {
            ragVectorIndexPort.deleteChunksByFileIdAndIngestionId(fileId, ingestionId);
        } catch (Exception cleanupFailure) {
            log.warn("IngestRagFileService: failed to cleanup staged chunks fileId={} ingestionId={}: {}",
                    fileId, ingestionId, cleanupFailure.getMessage());
        }
        try {
            tx.markFailed(fileId, ingestionId, e.getMessage());
        } catch (Exception txFailure) {
            log.error("IngestRagFileService: failed to mark FAILED fileId={} ingestionId={}: {}",
                    fileId, ingestionId, txFailure.getMessage());
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class Loader {

        private final FileJpaRepository fileRepository;

        @Transactional(readOnly = true)
        public Snapshot loadSnapshot(UUID fileId) {
            File file = fileRepository.findByIdWithUploaderAndTenant(fileId).orElse(null);
            if (file == null) {
                return null;
            }
            return new Snapshot(
                    file.getStatus(),
                    file.getId(),
                    file.getTenant().getId(),
                    file.getFolder() == null ? null : file.getFolder().getId(),
                    file.getUploadedBy().getId(),
                    file.getOriginalName(),
                    file.getContentKey(),
                    file.getCreatedAt()
            );
        }

        public record Snapshot(
                FileStatus status,
                UUID id,
                UUID tenantId,
                UUID folderId,
                UUID uploadedBy,
                String originalName,
                String contentKey,
                Instant createdAt
        ) {
        }
    }
}
