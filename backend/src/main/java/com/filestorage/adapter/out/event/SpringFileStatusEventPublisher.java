package com.filestorage.adapter.out.event;

import com.filestorage.domain.event.FileStatusChangedEvent;
import com.filestorage.domain.port.out.FileStatusEventPublisherPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SpringFileStatusEventPublisher implements FileStatusEventPublisherPort {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publishStatusChanged(FileStatusChangedEvent event) {
        log.debug("Publishing FileStatusChangedEvent for fileId={} status={}",
                event.fileId(), event.status());
        applicationEventPublisher.publishEvent(event);
    }
}
