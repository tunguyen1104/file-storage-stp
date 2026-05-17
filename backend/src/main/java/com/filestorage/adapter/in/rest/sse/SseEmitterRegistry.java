package com.filestorage.adapter.in.rest.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class SseEmitterRegistry {

    private final Map<UUID, CopyOnWriteArrayList<RegisteredEmitter>> emittersByFile = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> userActiveCount = new ConcurrentHashMap<>();

    public void register(UUID fileId, UUID userId, SseEmitter emitter) {
        emittersByFile
                .computeIfAbsent(fileId, k -> new CopyOnWriteArrayList<>())
                .add(new RegisteredEmitter(userId, emitter));
        userActiveCount
                .computeIfAbsent(userId, k -> new AtomicInteger())
                .incrementAndGet();
        log.debug("Registered SSE emitter for fileId={} userId={} (now {} for this user)",
                fileId, userId, userActiveCount.get(userId).get());
    }

    public void remove(UUID fileId, SseEmitter emitter) {
        CopyOnWriteArrayList<RegisteredEmitter> list = emittersByFile.get(fileId);
        if (list == null) {
            return;
        }
        list.removeIf(re -> {
            if (re.emitter == emitter) {
                decrementUser(re.userId);
                return true;
            }
            return false;
        });
        if (list.isEmpty()) {
            emittersByFile.remove(fileId, list);
        }
    }

    public void emit(UUID fileId, String eventName, Object data) {
        CopyOnWriteArrayList<RegisteredEmitter> list = emittersByFile.get(fileId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (RegisteredEmitter re : list) {
            try {
                re.emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException e) {
                log.debug("SSE emit failed for fileId={} userId={}, evicting: {}",
                        fileId, re.userId, e.getMessage());
                list.remove(re);
                decrementUser(re.userId);
                try {
                    re.emitter.completeWithError(e);
                } catch (Exception ignored) {
                    // already completed
                }
            }
        }
        if (list.isEmpty()) {
            emittersByFile.remove(fileId, list);
        }
    }

    public int activeCountForUser(UUID userId) {
        AtomicInteger c = userActiveCount.get(userId);
        return c == null ? 0 : c.get();
    }

    private void decrementUser(UUID userId) {
        AtomicInteger counter = userActiveCount.get(userId);
        if (counter == null) {
            return;
        }
        if (counter.decrementAndGet() <= 0) {
            userActiveCount.remove(userId, counter);
        }
    }

    private record RegisteredEmitter(UUID userId, SseEmitter emitter) {
    }
}
