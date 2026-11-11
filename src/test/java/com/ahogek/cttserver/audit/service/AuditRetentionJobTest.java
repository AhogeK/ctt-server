package com.ahogek.cttserver.audit.service;

import com.ahogek.cttserver.audit.repository.AuditLogRepository;
import com.ahogek.cttserver.common.config.properties.AuditRetentionProperties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
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

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("Audit retention")
class AuditRetentionJobTest {

    private static final long SWEEP_INTERVAL_MS = 3_600_000;
    private static final long INITIAL_DELAY_MS = 3_600_000;

    @Mock private AuditLogRepository repository;

    @Mock private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName(
            "Should keep every row and stay informational when no retention period is configured")
    void shouldKeepEveryRow_whenRetentionIsDisabled(CapturedOutput output) {
        AuditRetentionJob job = newJob(0, 1000, new MockEnvironment());

        job.logEffectivePolicy();
        job.purgeExpired();

        verifyNoInteractions(repository);
        assertThat(output.toString())
                .containsPattern(
                        "(?m)^.* INFO .*Audit retention is disabled \\(ctt\\.audit\\.retention\\.days=0\\)")
                .doesNotMatch("(?m)^.* WARN .*Audit retention is disabled");
    }

    @Test
    @DisplayName("Should delete old rows in batches when a retention period is configured")
    void shouldDeleteOldRowsInBatches_whenRetentionIsConfigured() {
        when(repository.deleteBatchBefore(any(), eq(2))).thenReturn(2, 0);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        AuditRetentionJob job = newJob(30, 2, new MockEnvironment());

        job.purgeExpired();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository, times(2)).deleteBatchBefore(cutoff.capture(), eq(2));
        assertThat(cutoff.getValue())
                .isAfter(Instant.now().minus(Duration.ofDays(31)))
                .isBefore(Instant.now().minus(Duration.ofDays(29)));
    }

    @Test
    @DisplayName("Should log the sweep result when no row is older than the cutoff")
    void shouldLogSweepResult_whenNothingIsDue(CapturedOutput output) {
        when(repository.deleteBatchBefore(any(), eq(1000))).thenReturn(0);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        newJob(180, 1000, new MockEnvironment()).purgeExpired();

        verify(repository, times(1)).deleteBatchBefore(any(), eq(1000));
        assertThat(output).contains("cutoff=").contains("deleted=0");
    }

    @Test
    @DisplayName("Should report the cutoff and progress when a batch fails")
    void shouldReportProgress_whenABatchFails(CapturedOutput output) {
        when(repository.deleteBatchBefore(any(), eq(2)))
                .thenReturn(2)
                .thenThrow(new IllegalStateException("database unavailable"));
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        AuditRetentionJob job = newJob(30, 2, new MockEnvironment());

        assertThatThrownBy(job::purgeExpired)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        assertThat(output.toString())
                .containsPattern(
                        "(?m)^.* ERROR .*Audit retention sweep failed: cutoff=.*, deleted=2 rows before the failure");
    }

    @Test
    @DisplayName("Should report the effective period at startup when retention is enabled")
    void shouldReportThePolicy_whenRetentionIsEnabled(CapturedOutput output) {
        newJob(180, 1000, new MockEnvironment()).logEffectivePolicy();

        assertThat(output)
                .contains(
                        "Audit retention enabled: deleting rows older than 180 days in batches of 1000");
    }

    @Test
    @DisplayName("Should warn when production keeps every audit row")
    void shouldWarn_whenProductionKeepsEveryRow(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        newJob(0, 1000, environment).logEffectivePolicy();

        assertThat(output.toString())
                .containsPattern(
                        "(?m)^.* WARN .*Audit retention is disabled \\(ctt\\.audit\\.retention\\.days=0\\)");
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
                                        new MockEnvironment(),
                                        0,
                                        INITIAL_DELAY_MS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AuditRetentionJob newJob(int retentionDays, int batchSize, Environment environment) {
        return new AuditRetentionJob(
                repository,
                new AuditRetentionProperties(retentionDays, batchSize),
                transactionManager,
                environment,
                SWEEP_INTERVAL_MS,
                INITIAL_DELAY_MS);
    }
}
