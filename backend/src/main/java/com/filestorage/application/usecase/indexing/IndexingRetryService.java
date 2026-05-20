package com.filestorage.application.usecase.indexing;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.domain.event.FileIndexEvent;
import com.filestorage.domain.port.out.IndexingEventPublisherPort;
import com.filestorage.infrastructure.config.IndexingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Safety net for indexing messages that were never delivered to the queue —
 * usually because the {@code afterCommit} hook silently failed or the app
 * crashed between DB commit and RabbitMQ publish.
 *
 * <p>Different failure mode from the RabbitMQ retry interceptor, which only
 * retries an in-flight message that a consumer rejected. This service finds
 * files that <i>never</i> had a message in the queue and re-publishes a fresh
 * one.
 *
 * <p>Eligibility: {@code is_indexed=false} AND {@code index_retry_count <
 * max-attempts} AND {@code created_at < now - eligible-after-minutes}. The
 * time bound stops us from racing the after-commit publisher.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IndexingRetryService {

    private final FileJpaRepository fileRepository;
    private final IndexingEventPublisherPort indexingEventPublisher;
    private final IndexingProperties indexingProperties;

    /**
     * @return number of messages re-published this run.
     */
    @Transactional(readOnly = true)
    public int republishLostMessages() {
        Instant cutoff = Instant.now().minus(
                indexingProperties.getRetry().getEligibleAfterMinutes(), ChronoUnit.MINUTES);
        int maxAttempts = indexingProperties.getRetry().getMaxAttempts();

        List<File> stuck = fileRepository.findUnindexedWithUploaderAndTenant(maxAttempts, cutoff);

        int published = 0;
        for (File f : stuck) {
            try {
                indexingEventPublisher.publishFileUploaded(
                        new FileIndexEvent(f.getId(), f.getTenant().getId(), f.getUploadedBy().getId(), FileIndexEvent.Action.UPSERT_CONTENT));
                published++;
            } catch (Exception e) {
                log.warn("IndexingRetryService: failed to re-publish fileId={}: {}", f.getId(), e.getMessage());
            }
        }
        if (published > 0) {
            log.info("IndexingRetryService: re-published {} of {} candidate(s)", published, stuck.size());
        }
        return published;
    }
}
