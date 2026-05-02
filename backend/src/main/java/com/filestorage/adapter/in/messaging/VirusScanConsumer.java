package com.filestorage.adapter.in.messaging;

import com.filestorage.application.usecase.file.VirusScanResultHandler;
import com.filestorage.domain.event.VirusScanCompletedEvent;
import com.filestorage.domain.event.VirusScanRequestedEvent;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.domain.port.out.VirusScanEventPublisherPort;
import com.filestorage.domain.port.out.VirusScanPort;
import com.filestorage.infrastructure.config.VirusScanRabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/**
 * Inbound RabbitMQ adapter — consumes {@link VirusScanRequestedEvent} messages,
 * downloads the file from MinIO, scans it via {@link VirusScanPort},
 * then publishes a {@link VirusScanCompletedEvent}; and consumes the result event
 * to delegate to {@link VirusScanResultHandler}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class VirusScanConsumer {

    private final ObjectStoragePort objectStoragePort;
    private final VirusScanPort virusScanPort;
    private final VirusScanEventPublisherPort virusScanEventPublisherPort;
    private final VirusScanResultHandler virusScanResultHandler;

    @RabbitListener(queues = VirusScanRabbitMQConfig.VIRUS_SCAN_REQUEST_QUEUE)
    public void handleVirusScanRequested(VirusScanRequestedEvent event) {
        log.info("VirusScanConsumer: received scan request for fileId={}", event.fileId());

        byte[] fileBytes;
        try {
            fileBytes = objectStoragePort.download(event.storageKey());
        } catch (Exception e) {
            log.error("VirusScanConsumer: failed to download file for fileId={} storageKey={}: {}",
                    event.fileId(), event.storageKey(), e.getMessage());
            // Re-throw so the retry interceptor can attempt again and eventually route to DLQ
            throw new RuntimeException("Failed to download file for virus scan: " + event.fileId(), e);
        }

        boolean infected;
        try {
            infected = virusScanPort.scan(new ByteArrayInputStream(fileBytes));
        } catch (Exception e) {
            log.error("VirusScanConsumer: scan failed for fileId={}: {}", event.fileId(), e.getMessage());
            throw new RuntimeException("Virus scan failed for file: " + event.fileId(), e);
        }

        log.info("VirusScanConsumer: scan completed for fileId={} infected={}", event.fileId(), infected);

        virusScanEventPublisherPort.publishVirusScanCompleted(new VirusScanCompletedEvent(
                event.fileId(),
                event.storageKey(),
                event.tenantId(),
                event.userId(),
                infected
        ));
    }

    @RabbitListener(queues = VirusScanRabbitMQConfig.VIRUS_SCAN_RESULT_QUEUE)
    public void handleVirusScanCompleted(VirusScanCompletedEvent event) {
        log.info("VirusScanConsumer: received scan result for fileId={} infected={}", event.fileId(), event.infected());
        virusScanResultHandler.handle(event);
    }
}
