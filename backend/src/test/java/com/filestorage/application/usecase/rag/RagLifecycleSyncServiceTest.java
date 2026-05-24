package com.filestorage.application.usecase.rag;

import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.port.out.RagVectorIndexPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagLifecycleSyncServiceTest {

    @Mock
    private RagFileTxHelper tx;
    @Mock
    private RagVectorIndexPort ragVectorIndexPort;
    @Mock
    private RagIndexRequestService ragIndexRequestService;

    private RagLifecycleSyncService service;

    @BeforeEach
    void setUp() {
        service = new RagLifecycleSyncService(tx, ragVectorIndexPort, ragIndexRequestService);
    }

    @Test
    void softDeleteHidesChunksAndMarksPendingDelete() {
        UUID fileId = UUID.randomUUID();

        service.onFileSoftDeleted(fileId);

        verify(tx).markPendingDelete(fileId);
        verify(ragVectorIndexPort).updateFileRetrievability(fileId, false, FileStatus.PENDING_DELETE);
    }

    @Test
    void restoreReusesExistingChunksWhenAvailable() {
        UUID fileId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        when(tx.canRestoreExistingChunks(fileId)).thenReturn(true);
        when(ragVectorIndexPort.hasReadyChunksByFileId(fileId)).thenReturn(true);

        service.onFileRestored(fileId, tenantId, userId, "contents/t/u/f.txt", "doc.pdf", folderId);

        verify(tx).markRestored(fileId);
        verify(ragVectorIndexPort).updateFileMetadata(fileId, "doc.pdf", folderId);
        verify(ragVectorIndexPort).updateFileRetrievability(fileId, true, FileStatus.ACTIVE);
        verify(ragIndexRequestService, never()).requestIndex(fileId, tenantId, userId, "contents/t/u/f.txt");
    }

    @Test
    void restoreQueuesReindexWhenChunksAreMissing() {
        UUID fileId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(tx.canRestoreExistingChunks(fileId)).thenReturn(true);
        when(ragVectorIndexPort.hasReadyChunksByFileId(fileId)).thenReturn(false);

        service.onFileRestored(fileId, tenantId, userId, "contents/t/u/f.txt", "doc.pdf", null);

        verify(tx, never()).markRestored(fileId);
        verify(ragIndexRequestService).requestIndex(fileId, tenantId, userId, "contents/t/u/f.txt");
    }

    @Test
    void hardDeleteDeletesChunksAndMarksDeleted() {
        UUID fileId = UUID.randomUUID();

        service.onFileHardDeleted(fileId);

        verify(ragVectorIndexPort).deleteChunksByFileId(fileId);
        verify(tx).markDeleted(fileId);
    }
}
