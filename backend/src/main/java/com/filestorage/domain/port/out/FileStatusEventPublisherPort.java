package com.filestorage.domain.port.out;

import com.filestorage.domain.event.FileStatusChangedEvent;

public interface FileStatusEventPublisherPort {
    void publishStatusChanged(FileStatusChangedEvent event);
}
