package com.filestorage.adapter.in.messaging;

import com.filestorage.application.usecase.indexing.DeleteFolderIndexService;
import com.filestorage.application.usecase.indexing.UpsertFolderIndexService;
import com.filestorage.domain.event.FolderIndexEvent;
import com.filestorage.infrastructure.config.IndexingRabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Inbound RabbitMQ adapter for folder indexing events.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FolderIndexingConsumer {

    private final UpsertFolderIndexService upsertFolderIndexService;
    private final DeleteFolderIndexService deleteFolderIndexService;

    @RabbitListener(queues = IndexingRabbitMQConfig.QUEUE_FOLDER_UPSERTED,
            containerFactory = IndexingRabbitMQConfig.LISTENER_FACTORY)
    public void onFolderUpserted(FolderIndexEvent event) {
        log.info("FolderIndexingConsumer: onFolderUpserted folderId={}", event.folderId());
        upsertFolderIndexService.execute(event.folderId());
    }

    @RabbitListener(queues = IndexingRabbitMQConfig.QUEUE_FOLDER_DELETED,
            containerFactory = IndexingRabbitMQConfig.LISTENER_FACTORY)
    public void onFolderDeleted(FolderIndexEvent event) {
        log.info("FolderIndexingConsumer: onFolderDeleted folderId={}", event.folderId());
        deleteFolderIndexService.execute(event.folderId(), event.tenantId(), event.userId());
    }
}
