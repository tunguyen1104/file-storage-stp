package com.filestorage.adapter.out.scheduling;

import com.filestorage.adapter.out.persistence.repository.FileShareJpaRepository;
import com.filestorage.domain.model.enums.ShareStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled job that materializes naturally expired shares.
 * <p>
 * Access checks still validate {@code expiresAt > now}; this job keeps persisted
 * lifecycle state and active-share unique indexes aligned shortly after expiry.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ExpiredShareCleanupJob {

    private final FileShareJpaRepository shareRepository;

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void expireShares() {
        int expiredCount = shareRepository.expireActiveShares(
                ShareStatus.ACTIVE,
                ShareStatus.EXPIRED,
                Instant.now()
        );

        if (expiredCount > 0) {
            log.info("ExpiredShareCleanupJob: expired {} share(s)", expiredCount);
        }
    }
}
