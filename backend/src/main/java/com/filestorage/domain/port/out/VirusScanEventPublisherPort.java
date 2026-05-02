package com.filestorage.domain.port.out;

import com.filestorage.domain.event.VirusScanCompletedEvent;
import com.filestorage.domain.event.VirusScanRequestedEvent;

/**
 * Outbound port for publishing virus-scan lifecycle events to the message broker.
 */
public interface VirusScanEventPublisherPort {

    /**
     * Publishes a request for asynchronous virus scanning.
     * Called by {@code ConfirmUploadService} after moving the file to SCANNING status.
     */
    void publishVirusScanRequested(VirusScanRequestedEvent event);

    /**
     * Publishes the result of a completed virus scan.
     * Called by {@code VirusScanConsumer} after the scan finishes.
     */
    void publishVirusScanCompleted(VirusScanCompletedEvent event);
}
