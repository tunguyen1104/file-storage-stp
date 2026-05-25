package com.filestorage.adapter.in.messaging;

import com.filestorage.application.usecase.rag.IngestRagFileService;
import com.filestorage.domain.event.RagIndexEvent;
import com.filestorage.infrastructure.config.RagRabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RagIndexingConsumer {

    private final IngestRagFileService ingestRagFileService;

    @RabbitListener(queues = RagRabbitMQConfig.QUEUE_RAG_FILE_INGEST,
            containerFactory = RagRabbitMQConfig.LISTENER_FACTORY)
    public void onFileIngestRequested(RagIndexEvent event) {
        log.info("RagIndexingConsumer: onFileIngestRequested fileId={} action={}", event.fileId(), event.action());
        if (event.action() != RagIndexEvent.Action.INGEST) {
            log.warn("RagIndexingConsumer: unsupported action={} fileId={}", event.action(), event.fileId());
            return;
        }
        ingestRagFileService.execute(event.fileId());
    }
}
