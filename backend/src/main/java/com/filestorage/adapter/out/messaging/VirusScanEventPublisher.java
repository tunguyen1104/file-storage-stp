package com.filestorage.adapter.out.messaging;

import com.filestorage.domain.event.VirusScanCompletedEvent;
import com.filestorage.domain.event.VirusScanRequestedEvent;
import com.filestorage.domain.port.out.VirusScanEventPublisherPort;
import com.filestorage.infrastructure.config.VirusScanRabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Outbound messaging adapter — publishes virus-scan events to RabbitMQ.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class VirusScanEventPublisher implements VirusScanEventPublisherPort {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishVirusScanRequested(VirusScanRequestedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    VirusScanRabbitMQConfig.VIRUS_SCAN_EXCHANGE,
                    VirusScanRabbitMQConfig.VIRUS_SCAN_REQUEST_RK,
                    event
            );
            log.info("Published VirusScanRequestedEvent for fileId={}", event.fileId());
        } catch (Exception e) {
            log.error("Failed to publish VirusScanRequestedEvent for fileId={}: {}", event.fileId(), e.getMessage());
            throw e;
        }
    }

    @Override
    public void publishVirusScanCompleted(VirusScanCompletedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    VirusScanRabbitMQConfig.VIRUS_SCAN_EXCHANGE,
                    VirusScanRabbitMQConfig.VIRUS_SCAN_RESULT_RK,
                    event
            );
            log.info("Published VirusScanCompletedEvent for fileId={} infected={}", event.fileId(), event.infected());
        } catch (Exception e) {
            log.error("Failed to publish VirusScanCompletedEvent for fileId={}: {}", event.fileId(), e.getMessage());
            throw e;
        }
    }
}
