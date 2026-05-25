package com.filestorage.application.usecase.rag;

import com.filestorage.domain.event.RagIndexEvent;
import com.filestorage.domain.port.out.RagIndexingEventPublisherPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Slf4j
@RequiredArgsConstructor
public class AfterCommitRagIndexingPublisher {

    private final RagIndexingEventPublisherPort delegate;

    public void publishFileIngestRequested(RagIndexEvent event) {
        runAfterCommit(() -> delegate.publishFileIngestRequested(event), "publishFileIngestRequested(" + event.fileId() + ")");
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
                    log.error("RAG indexing publish failed in afterCommit for {}: {}", label, e.getMessage(), e);
                }
            }
        });
    }
}
