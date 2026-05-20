package com.filestorage.application.usecase.indexing;

import com.filestorage.domain.event.FileIndexEvent;
import com.filestorage.domain.event.FolderIndexEvent;
import com.filestorage.domain.port.out.IndexingEventPublisherPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Decorator that defers every indexing publish call to the {@code afterCommit}
 * phase of the current transaction.
 *
 * <p>This is the bean application services should depend on — the raw
 * {@link IndexingEventPublisherPort} is never injected directly into use cases.
 *
 * <p>If no transaction is active (e.g. called from a scheduler), publish runs
 * inline. This matches the existing pattern in the codebase: write-path
 * services always run inside a transaction; transaction-less callers (e.g.
 * the safety-net retry scheduler) want immediate publish anyway.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AfterCommitIndexingPublisher {

    private final IndexingEventPublisherPort delegate;

    public void publishFileUploaded(FileIndexEvent event) {
        runAfterCommit(() -> delegate.publishFileUploaded(event), "publishFileUploaded(" + event.fileId() + ")");
    }

    public void publishFileUpdated(FileIndexEvent event) {
        runAfterCommit(() -> delegate.publishFileUpdated(event), "publishFileUpdated(" + event.fileId() + ")");
    }

    public void publishFileDeleted(FileIndexEvent event) {
        runAfterCommit(() -> delegate.publishFileDeleted(event), "publishFileDeleted(" + event.fileId() + ")");
    }

    public void publishFolderUpserted(FolderIndexEvent event) {
        runAfterCommit(() -> delegate.publishFolderUpserted(event), "publishFolderUpserted(" + event.folderId() + ")");
    }

    public void publishFolderDeleted(FolderIndexEvent event) {
        runAfterCommit(() -> delegate.publishFolderDeleted(event), "publishFolderDeleted(" + event.folderId() + ")");
    }

    private void runAfterCommit(Runnable publish, String label) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.debug("No active transaction for {} — publishing inline", label);
            publish.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    publish.run();
                } catch (Exception e) {
                    // Don't propagate — afterCommit cannot fail the transaction (already committed).
                    // The safety-net IndexingRetryService will re-publish lost messages on its next tick.
                    log.error("Indexing publish failed in afterCommit for {}: {}", label, e.getMessage(), e);
                }
            }
        });
    }
}
