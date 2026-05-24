package com.filestorage.application.usecase.rag;

import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.port.out.RagVectorIndexPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RagLifecycleSyncService {

    private final RagFileTxHelper tx;
    private final RagVectorIndexPort ragVectorIndexPort;
    private final RagIndexRequestService ragIndexRequestService;

    public void onFileSoftDeleted(UUID fileId) {
        runAfterCommit("hide RAG chunks for soft-deleted file " + fileId, () -> {
            tx.markPendingDelete(fileId);
            ragVectorIndexPort.updateFileRetrievability(fileId, false, FileStatus.PENDING_DELETE);
        });
    }

    public void onFileRestored(
            UUID fileId,
            UUID tenantId,
            UUID userId,
            String contentKey,
            String originalName,
            UUID folderId
    ) {
        runAfterCommit("restore or reindex RAG chunks for file " + fileId, () -> {
            if (canRestoreExistingChunks(fileId)) {
                tx.markRestored(fileId);
                ragVectorIndexPort.updateFileMetadata(fileId, originalName, folderId);
                ragVectorIndexPort.updateFileRetrievability(fileId, true, FileStatus.ACTIVE);
                return;
            }
            ragIndexRequestService.requestIndex(fileId, tenantId, userId, contentKey);
        });
    }

    public void onFileHardDeleted(UUID fileId) {
        try {
            ragVectorIndexPort.deleteChunksByFileId(fileId);
        } finally {
            tx.markDeleted(fileId);
        }
    }

    public void onFileMetadataUpdated(UUID fileId, String originalName, UUID folderId) {
        ragVectorIndexPort.updateFileMetadata(fileId, originalName, folderId);
    }

    private boolean canRestoreExistingChunks(UUID fileId) {
        if (!tx.canRestoreExistingChunks(fileId)) {
            return false;
        }
        try {
            return ragVectorIndexPort.hasReadyChunksByFileId(fileId);
        } catch (Exception e) {
            log.warn("RagLifecycleSyncService: failed to check existing chunks fileId={}: {}", fileId, e.getMessage());
            return false;
        }
    }

    private void runAfterCommit(String label, Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (Exception e) {
                    log.warn("RagLifecycleSyncService: {} failed: {}", label, e.getMessage());
                }
            }
        });
    }
}
