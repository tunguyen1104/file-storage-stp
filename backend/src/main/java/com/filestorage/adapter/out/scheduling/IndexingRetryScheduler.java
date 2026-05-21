package com.filestorage.adapter.out.scheduling;

import com.filestorage.application.usecase.indexing.IndexingRetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Re-publishes indexing events for files whose original event was never
 * delivered to the queue — usually because the {@code afterCommit} hook
 * silently failed or the app crashed between DB commit and RabbitMQ publish.
 *
 * <p>This is <b>not</b> a duplicate of RabbitMQ's retry interceptor: that
 * interceptor handles in-flight message failures (consumer threw); this
 * scheduler handles "message never sent at all" — a different failure mode.
 *
 * <p>Wrapped in {@link SchedulerLock} so multi-node deployments don't double-publish.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class IndexingRetryScheduler {

    private final IndexingRetryService indexingRetryService;

    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    @SchedulerLock(name = "indexingRetryScheduler", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void replayLostIndexingMessages() {
        try {
            int republished = indexingRetryService.republishLostMessages();
            if (republished > 0) {
                log.info("IndexingRetryScheduler: re-published {} lost indexing message(s)", republished);
            }
        } catch (Exception e) {
            log.error("IndexingRetryScheduler: unexpected failure: {}", e.getMessage(), e);
        }
    }
}
