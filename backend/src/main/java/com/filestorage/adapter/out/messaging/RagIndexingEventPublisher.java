package com.filestorage.adapter.out.messaging;

import com.filestorage.domain.event.RagIndexEvent;
import com.filestorage.domain.port.out.RagIndexingEventPublisherPort;
import com.filestorage.infrastructure.config.RagRabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RagIndexingEventPublisher implements RagIndexingEventPublisherPort {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishFileIngestRequested(RagIndexEvent event) {
        try {
            rabbitTemplate.convertAndSend(RagRabbitMQConfig.RAG_EXCHANGE, RagRabbitMQConfig.RK_RAG_FILE_INGEST, event);
            log.info("Published RagIndexEvent(INGEST) fileId={} rk={}", event.fileId(), RagRabbitMQConfig.RK_RAG_FILE_INGEST);
        } catch (Exception e) {
            log.error("Failed to publish RagIndexEvent(INGEST) fileId={}: {}", event.fileId(), e.getMessage());
            throw e;
        }
    }
}
