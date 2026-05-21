package com.filestorage.adapter.in.messaging;

import com.filestorage.application.usecase.indexing.DeleteFileIndexService;
import com.filestorage.application.usecase.indexing.IndexFileService;
import com.filestorage.application.usecase.indexing.UpdateFileIndexService;
import com.filestorage.domain.event.FileIndexEvent;
import com.filestorage.infrastructure.config.IndexingRabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Inbound RabbitMQ adapter — one listener per queue, delegating to the
 * matching indexing service. Mirrors the {@code VirusScanConsumer} pattern so
 * style stays consistent across the codebase.
 *
 * <p>All three listeners share the {@code indexingListenerContainerFactory}
 * which carries the retry interceptor (3× with 5 s / 15 s / 45 s back-off) and
 * routes exhausted failures to {@code file-storage.indexing.dlq}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FileIndexingConsumer {

    private final IndexFileService indexFileService;
    private final UpdateFileIndexService updateFileIndexService;
    private final DeleteFileIndexService deleteFileIndexService;

    @RabbitListener(queues = IndexingRabbitMQConfig.QUEUE_FILE_UPLOADED,
            containerFactory = IndexingRabbitMQConfig.LISTENER_FACTORY)
    public void onFileUploaded(FileIndexEvent event) {
        log.info("FileIndexingConsumer: onFileUploaded fileId={}", event.fileId());
        indexFileService.execute(event.fileId());
    }

    @RabbitListener(queues = IndexingRabbitMQConfig.QUEUE_FILE_UPDATED,
            containerFactory = IndexingRabbitMQConfig.LISTENER_FACTORY)
    public void onFileUpdated(FileIndexEvent event) {
        log.info("FileIndexingConsumer: onFileUpdated fileId={}", event.fileId());
        updateFileIndexService.execute(event.fileId(), event.userId());
    }

    @RabbitListener(queues = IndexingRabbitMQConfig.QUEUE_FILE_DELETED,
            containerFactory = IndexingRabbitMQConfig.LISTENER_FACTORY)
    public void onFileDeleted(FileIndexEvent event) {
        log.info("FileIndexingConsumer: onFileDeleted fileId={}", event.fileId());
        deleteFileIndexService.execute(event.fileId(), event.tenantId(), event.userId());
    }
}
