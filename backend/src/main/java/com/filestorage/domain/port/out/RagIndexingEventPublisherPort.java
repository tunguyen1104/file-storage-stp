package com.filestorage.domain.port.out;

import com.filestorage.domain.event.RagIndexEvent;

public interface RagIndexingEventPublisherPort {

    void publishFileIngestRequested(RagIndexEvent event);
}
