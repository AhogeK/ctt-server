package com.ahogek.cttserver.audit.service;

import com.ahogek.cttserver.audit.repository.AuditLogRepository;
import com.ahogek.cttserver.common.config.properties.AuditRetentionProperties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Audit retention")
class AuditRetentionJobTest {

    @Mock private AuditLogRepository repository;

    @Mock private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("Should keep every row when no retention period is configured")
    void shouldKeepEveryRow_whenRetentionIsDisabled() {
        AuditRetentionJob job =
                new AuditRetentionJob(
                        repository,
                        new AuditRetentionProperties(0, 1000),
                        transactionManager,
                        3_600_000,
                        3_600_000);

        job.purgeExpired();

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Should delete old rows in batches when a retention period is configured")
    void shouldDeleteOldRowsInBatches_whenRetentionIsConfigured() {
        when(repository.deleteBatchBefore(any(), eq(2))).thenReturn(2, 0);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        AuditRetentionJob job =
                new AuditRetentionJob(
                        repository,
                        new AuditRetentionProperties(30, 2),
                        transactionManager,
                        3_600_000,
                        3_600_000);

        job.purgeExpired();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository, times(2)).deleteBatchBefore(cutoff.capture(), eq(2));
        assertThat(cutoff.getValue())
                .isAfter(Instant.now().minus(Duration.ofDays(31)))
                .isBefore(Instant.now().minus(Duration.ofDays(29)));
    }

    @Test
    @DisplayName("Should reject a non-positive sweep interval")
    void shouldRejectNonPositiveSweepInterval() {
        assertThatThrownBy(
                        () ->
                                new AuditRetentionJob(
                                        repository,
                                        new AuditRetentionProperties(30, 1000),
                                        transactionManager,
                                        0,
                                        3_600_000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
