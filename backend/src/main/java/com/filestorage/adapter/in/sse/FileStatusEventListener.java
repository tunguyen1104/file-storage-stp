package com.filestorage.adapter.in.sse;

import com.filestorage.adapter.in.rest.sse.SseEmitterRegistry;
import com.filestorage.domain.event.FileStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * In-process inbound adapter that bridges Spring's {@code ApplicationEventPublisher} to the
 * local {@link SseEmitterRegistry}.
 * <p>
 * Fires <strong>after</strong> the transaction that produced the status change commits, so
 * subscribers never see a status that has not yet been persisted. {@code fallbackExecution =
 * true} keeps the listener usable from non-transactional callers (mainly unit tests) — in
 * that case it runs synchronously like a plain {@code @EventListener}.
 * <p>
 * Threading: by default this runs on the thread that committed the transaction (typically a
 * RabbitMQ listener thread for the scan-result path). {@link SseEmitterRegistry#emit} is
 * non-blocking — a closed emitter is evicted, not awaited — so this is safe to invoke
 * inline.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FileStatusEventListener {

    private final SseEmitterRegistry registry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onFileStatusChanged(FileStatusChangedEvent event) {
        log.debug("Received FileStatusChangedEvent for fileId={} status={}",
                event.fileId(), event.status());
        registry.emit(event.fileId(), "status", event);
    }
}
