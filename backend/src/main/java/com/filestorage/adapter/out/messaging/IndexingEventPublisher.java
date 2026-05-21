package com.filestorage.adapter.out.messaging;

import com.filestorage.domain.event.FileIndexEvent;
import com.filestorage.domain.event.FolderIndexEvent;
import com.filestorage.domain.port.out.IndexingEventPublisherPort;
import com.filestorage.infrastructure.config.IndexingRabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Outbound RabbitMQ publisher for indexing events. The application layer goes
 * through {@code AfterCommitIndexingPublisher} (decorator) so messages only
 * fire after the JPA commit succeeds — never inside an open transaction and
 * never on rollback.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class IndexingEventPublisher implements IndexingEventPublisherPort {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishFileUploaded(FileIndexEvent event) {
        send(IndexingRabbitMQConfig.RK_FILE_UPLOADED, event, "FileIndexEvent(UPSERT_CONTENT)");
    }

    @Override
    public void publishFileUpdated(FileIndexEvent event) {
        send(IndexingRabbitMQConfig.RK_FILE_UPDATED, event, "FileIndexEvent(UPSERT_METADATA)");
    }

    @Override
    public void publishFileDeleted(FileIndexEvent event) {
        send(IndexingRabbitMQConfig.RK_FILE_DELETED, event, "FileIndexEvent(DELETE)");
    }

    @Override
    public void publishFolderUpserted(FolderIndexEvent event) {
        send(IndexingRabbitMQConfig.RK_FOLDER_UPSERTED, event, "FolderIndexEvent(UPSERT)");
    }

    @Override
    public void publishFolderDeleted(FolderIndexEvent event) {
        send(IndexingRabbitMQConfig.RK_FOLDER_DELETED, event, "FolderIndexEvent(DELETE)");
    }

    private void send(String routingKey, Object event, String label) {
        try {
            rabbitTemplate.convertAndSend(IndexingRabbitMQConfig.INDEXING_EXCHANGE, routingKey, event);
            log.info("Published {} on rk={}", label, routingKey);
        } catch (Exception e) {
            log.error("Failed to publish {} on rk={}: {}", label, routingKey, e.getMessage());
            throw e;
        }
    }
}
