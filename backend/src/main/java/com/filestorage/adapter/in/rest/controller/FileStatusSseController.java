package com.filestorage.adapter.in.rest.controller;

import com.filestorage.adapter.in.rest.dto.response.FileStatusResponse;
import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.adapter.in.rest.sse.SseEmitterRegistry;
import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import com.filestorage.shared.util.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class FileStatusSseController {

    /** Per-emitter open lifetime. Spring closes the emitter automatically when this elapses. */
    private static final long EMITTER_TIMEOUT_MS = 4L * 60L * 1000L;

    /** Heartbeat interval — keep proxies / load balancers from idle-killing the TCP connection. */
    private static final long HEARTBEAT_INTERVAL_SECONDS = 20L;

    /** Wait this long after open before starting the DB safety-net poll. */
    private static final long DB_FALLBACK_INITIAL_DELAY_SECONDS = 30L;

    /** Then re-poll every N seconds. */
    private static final long DB_FALLBACK_PERIOD_SECONDS = 30L;

    /** Cap on concurrent SSE connections per user (DoS guard). */
    private static final int MAX_CONNECTIONS_PER_USER = 10;

    /** Terminal statuses — once a file reaches one of these, the emitter completes. */
    private static final Set<FileStatus> TERMINAL = Set.of(
            FileStatus.ACTIVE, FileStatus.INFECTED, FileStatus.UPLOAD_FAILED);

    // SSE event-name constants — keeps every send() call consistent and typo-proof.
    private static final String EVENT_STATUS = "status";
    private static final String EVENT_PING   = "ping";
    private static final String EVENT_ERROR  = "error";

    private final FileJpaRepository fileRepository;
    private final SseEmitterRegistry registry;

    private final ScheduledExecutorService scheduler;

    @GetMapping(value = "/files/{fileId}/status/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamFileStatus(@PathVariable UUID fileId) {
        SecurityUser actor = SecurityContextUtil.getCurrentUser()
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));
        UUID callerUserId = UUID.fromString(actor.getUserId());
        UUID callerTenantId = UUID.fromString(actor.getTenantId());

        File file = fileRepository.findByIdAndTenantId(fileId, callerTenantId)
                .orElseThrow(() -> new AppException(ErrorCode.FILE_NOT_FOUND));

        // Owner-only access — tenant admins do not have a need to monitor others' uploads.
        if (!file.getUploadedBy().getId().equals(callerUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        FileStatus currentStatus = file.getStatus();
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);

        // Fast path: file already in a terminal state — single event, then close.
        if (TERMINAL.contains(currentStatus)) {
            try {
                emitter.send(SseEmitter.event().name(EVENT_STATUS).data(buildPayload(file)));
            } catch (IOException e) {
                log.debug("Failed to send terminal status event for fileId={}: {}", fileId, e.getMessage());
            }
            emitter.complete();
            return emitter;
        }

        // Hot path: SCANNING. Cap concurrent connections per user before registering.
        if (registry.activeCountForUser(callerUserId) >= MAX_CONNECTIONS_PER_USER) {
            throw new AppException(ErrorCode.SSE_CONNECTION_LIMIT_EXCEEDED);
        }

        registry.register(fileId, callerUserId, emitter);

        // Send the current SCANNING state so clients are never blind about the starting point.
        try {
            emitter.send(SseEmitter.event().name(EVENT_STATUS).data(buildPayload(file)));
        } catch (IOException e) {
            log.debug("Failed to send initial status event for fileId={}: {}", fileId, e.getMessage());
            registry.remove(fileId, emitter);
            emitter.completeWithError(e);
            return emitter;
        }

        // Heartbeat — fires every 20 s.
        ScheduledFuture<?> heartbeatFuture = scheduler.scheduleAtFixedRate(
                () -> sendPing(fileId, emitter),
                HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS);

        // DB-fallback safety-net — defence-in-depth against a missed in-process event
        // (listener exception, restart race, emitter registered after the event fired).
        // Polls 3 times at 30 s, 60 s, 90 s after open. Each poll re-reads the status from
        // the DB and, if it has transitioned to a terminal state, pushes the event and closes.
        ScheduledFuture<?> dbFallbackFuture = scheduler.scheduleAtFixedRate(
                new DbFallbackTask(fileId, emitter, 3),
                DB_FALLBACK_INITIAL_DELAY_SECONDS,
                DB_FALLBACK_PERIOD_SECONDS,
                TimeUnit.SECONDS);

        // Lifecycle hooks: clean up registry + cancel scheduled tasks on completion / error / timeout.
        Runnable cleanup = () -> {
            heartbeatFuture.cancel(false);
            dbFallbackFuture.cancel(false);
            registry.remove(fileId, emitter);
        };
        emitter.onCompletion(cleanup);
        emitter.onError(e -> {
            log.debug("SSE emitter error for fileId={}: {}", fileId, e.getMessage());
            cleanup.run();
        });
        emitter.onTimeout(() -> {
            log.debug("SSE emitter timed out for fileId={}", fileId);
            try {
                emitter.send(SseEmitter.event().name(EVENT_ERROR)
                        .data(Map.of("code", "SCAN_TIMEOUT", "message", "Virus scan timed out")));
            } catch (IOException ignored) {
                // connection already dead — nothing to do
            }
            emitter.complete();
        });

        return emitter;
    }

    private void sendPing(UUID fileId, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name(EVENT_PING).data(Map.of()));
        } catch (IOException e) {
            log.debug("Heartbeat send failed for fileId={}, emitter likely closed: {}",
                    fileId, e.getMessage());
            registry.remove(fileId, emitter);
            emitter.completeWithError(e);
        }
    }

    /**
     * Re-reads the file's status from the DB and, if terminal, pushes the event and closes
     * the emitter. The task is also cancellable — once the file completes or the registered
     * count drops to zero it stops scheduling itself.
     */
    private final class DbFallbackTask implements Runnable {
        private final UUID fileId;
        private final SseEmitter emitter;
        private int attemptsRemaining;

        DbFallbackTask(UUID fileId, SseEmitter emitter, int maxAttempts) {
            this.fileId = fileId;
            this.emitter = emitter;
            this.attemptsRemaining = maxAttempts;
        }

        @Override
        public void run() {
            if (attemptsRemaining-- <= 0) {
                return;
            }
            try {
                File current = fileRepository.findById(fileId).orElse(null);
                if (current == null) {
                    return;
                }
                if (TERMINAL.contains(current.getStatus())) {
                    log.debug("DB fallback found terminal status {} for fileId={}, pushing",
                            current.getStatus(), fileId);
                    try {
                        emitter.send(SseEmitter.event().name(EVENT_STATUS).data(buildPayload(current)));
                    } catch (IOException ignored) {
                        log.debug("Failed to send DB fallback event for fileId={}: {}", fileId, ignored.getMessage());
                    }
                    emitter.complete();
                }
            } catch (Exception e) {
                log.debug("DB fallback poll failed for fileId={}: {}", fileId, e.getMessage());
            }
        }
    }

    private static FileStatusResponse buildPayload(File file) {
        return new FileStatusResponse(
                file.getId(),
                file.getStatus(),
                file.getOriginalName(),
                file.getUpdatedAt()
        );
    }
}
