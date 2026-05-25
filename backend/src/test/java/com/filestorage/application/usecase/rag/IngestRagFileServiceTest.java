package com.filestorage.application.usecase.rag;

import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.rag.EmbeddingVector;
import com.filestorage.domain.model.rag.RagChunkIndexData;
import com.filestorage.domain.model.rag.RagTextChunk;
import com.filestorage.domain.port.out.EmbeddingPort;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.domain.port.out.RagVectorIndexPort;
import com.filestorage.infrastructure.config.RagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestRagFileServiceTest {

    @Mock
    private IngestRagFileService.Loader loader;
    @Mock
    private ObjectStoragePort objectStoragePort;
    @Mock
    private PlainTextChunker chunker;
    @Mock
    private EmbeddingPort embeddingPort;
    @Mock
    private RagVectorIndexPort ragVectorIndexPort;
    @Mock
    private RagFileTxHelper tx;

    private RagProperties ragProperties;
    private IngestRagFileService service;

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
        ragProperties.getEmbedding().setBatchSize(2);
        service = new IngestRagFileService(
                loader,
                objectStoragePort,
                chunker,
                embeddingPort,
                ragVectorIndexPort,
                tx,
                ragProperties
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void activeFileIsChunkedEmbeddedAndIndexedWithStagingIngestion() {
        UUID fileId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID ingestionId = UUID.randomUUID();
        var snapshot = snapshot(fileId, tenantId, folderId, userId, FileStatus.ACTIVE, "contents/t/u/f.txt");

        when(loader.loadSnapshot(fileId)).thenReturn(snapshot, snapshot);
        when(tx.markIndexing(fileId, "contents/t/u/f.txt", "bge-m3", 1024, "v1")).thenReturn(ingestionId);
        when(objectStoragePort.download("contents/t/u/f.txt")).thenReturn("hello".getBytes(StandardCharsets.UTF_8));
        when(chunker.chunk("hello")).thenReturn(List.of(
                new RagTextChunk(0, "hello", 0, 5),
                new RagTextChunk(1, "world", 6, 11)
        ));
        when(embeddingPort.embed(List.of("hello", "world"))).thenReturn(List.of(
                new EmbeddingVector(0, List.of(0.1f)),
                new EmbeddingVector(1, List.of(0.2f))
        ));

        service.execute(fileId);

        ArgumentCaptor<List<RagChunkIndexData>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(ragVectorIndexPort).upsertChunks(chunksCaptor.capture());
        List<RagChunkIndexData> indexed = chunksCaptor.getValue();
        assertEquals(2, indexed.size());
        assertEquals(fileId, indexed.get(0).fileId());
        assertEquals(ingestionId, indexed.get(0).ingestionId());
        assertFalse(indexed.get(0).ready());

        verify(ragVectorIndexPort).updateFileMetadata(fileId, "doc.txt", folderId);
        verify(ragVectorIndexPort).markChunksReadyByFileIdAndIngestionId(fileId, ingestionId);
        verify(ragVectorIndexPort).deleteChunksByFileIdExceptIngestionId(fileId, ingestionId);
        verify(tx).markIndexed(fileId, ingestionId, 2);
    }

    @Test
    void activeFileWithoutContentKeyIsMarkedUnsupported() {
        UUID fileId = UUID.randomUUID();
        var snapshot = snapshot(fileId, UUID.randomUUID(), null, UUID.randomUUID(), FileStatus.ACTIVE, null);
        when(loader.loadSnapshot(fileId)).thenReturn(snapshot);

        service.execute(fileId);

        verify(ragVectorIndexPort).updateFileRetrievability(fileId, false, FileStatus.ACTIVE);
        verify(tx).markUnsupported(fileId, null, "No extracted text content");
        verify(objectStoragePort, never()).download(any());
    }

    private static IngestRagFileService.Loader.Snapshot snapshot(
            UUID fileId,
            UUID tenantId,
            UUID folderId,
            UUID userId,
            FileStatus status,
            String contentKey
    ) {
        return new IngestRagFileService.Loader.Snapshot(
                status,
                fileId,
                tenantId,
                folderId,
                userId,
                "doc.txt",
                contentKey,
                Instant.now()
        );
    }
}
