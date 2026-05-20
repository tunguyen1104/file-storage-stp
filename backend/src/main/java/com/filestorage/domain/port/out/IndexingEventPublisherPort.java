package com.filestorage.domain.port.out;

import com.filestorage.domain.event.FileIndexEvent;
import com.filestorage.domain.event.FolderIndexEvent;

/**
 * Outbound port for emitting indexing events. The adapter implements this on
 * top of RabbitMQ.
 *
 * <p>Application services do <b>not</b> call this directly — they go through
 * {@code AfterCommitIndexingPublisher} which defers every call to the
 * {@code afterCommit} hook so events never fire inside an open transaction.
 */
public interface IndexingEventPublisherPort {

    void publishFileUploaded(FileIndexEvent event);

    void publishFileUpdated(FileIndexEvent event);

    void publishFileDeleted(FileIndexEvent event);

    void publishFolderUpserted(FolderIndexEvent event);

    void publishFolderDeleted(FolderIndexEvent event);
}
