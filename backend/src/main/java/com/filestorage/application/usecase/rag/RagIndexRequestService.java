package com.filestorage.application.usecase.rag;

import com.filestorage.domain.event.RagIndexEvent;
import com.filestorage.infrastructure.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RagIndexRequestService {

    private final RagFileTxHelper tx;
    private final AfterCommitRagIndexingPublisher publisher;
    private final RagProperties ragProperties;

    @Transactional
    public void requestIndex(UUID fileId, UUID tenantId, UUID userId, String contentKey) {
        if (!ragProperties.isEnabled()) {
            log.debug("RagIndexRequestService: RAG disabled, skip fileId={}", fileId);
            return;
        }
        if (contentKey == null || contentKey.isBlank()) {
            tx.markUnsupported(fileId, contentKey, "No extracted text content");
            log.info("RagIndexRequestService: marked unsupported fileId={} reason=no contentKey", fileId);
            return;
        }

        tx.markPending(
                fileId,
                contentKey,
                ragProperties.getEmbedding().getModel(),
                ragProperties.getEmbedding().getDimensions(),
                ragProperties.getChunks().getVersion()
        );
        publisher.publishFileIngestRequested(new RagIndexEvent(
                fileId,
                tenantId,
                userId,
                RagIndexEvent.Action.INGEST
        ));
        log.info("RagIndexRequestService: queued RAG ingest fileId={}", fileId);
    }
}
