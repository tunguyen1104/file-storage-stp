package com.filestorage.adapter.out.scheduling;

import com.filestorage.adapter.out.persistence.repository.FileShareJpaRepository;
import com.filestorage.domain.model.enums.ShareStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiredShareCleanupJobTest {

    @Mock
    private FileShareJpaRepository shareRepository;

    private ExpiredShareCleanupJob job;

    @BeforeEach
    void setUp() {
        job = new ExpiredShareCleanupJob(shareRepository);
    }

    @Test
    void expireSharesMarksExpiredActiveShares() {
        when(shareRepository.expireActiveShares(
                eq(ShareStatus.ACTIVE),
                eq(ShareStatus.EXPIRED),
                any(Instant.class)
        )).thenReturn(3);

        job.expireShares();

        verify(shareRepository).expireActiveShares(
                eq(ShareStatus.ACTIVE),
                eq(ShareStatus.EXPIRED),
                any(Instant.class)
        );
    }

    @Test
    void expireSharesAllowsNoExpiredShares() {
        when(shareRepository.expireActiveShares(
                eq(ShareStatus.ACTIVE),
                eq(ShareStatus.EXPIRED),
                any(Instant.class)
        )).thenReturn(0);

        job.expireShares();

        verify(shareRepository).expireActiveShares(
                eq(ShareStatus.ACTIVE),
                eq(ShareStatus.EXPIRED),
                any(Instant.class)
        );
    }
}
