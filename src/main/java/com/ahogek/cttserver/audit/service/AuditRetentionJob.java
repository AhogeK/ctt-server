package com.ahogek.cttserver.audit.service;

import com.ahogek.cttserver.audit.repository.AuditLogRepository;
import com.ahogek.cttserver.common.config.properties.AuditRetentionProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

/**
 * Enforces the configured audit retention period.
 *
 * <p>Deletion is disabled while {@code ctt.audit.retention.days} is zero. That is deliberate: the
 * period is an operations/compliance decision, and an application default must not silently delete
 * security evidence. Rows are removed in bounded batches so a large table does not become one long
 * lock.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-09
 */
@Component
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

    private final AuditLogRepository repository;
    private final AuditRetentionProperties properties;
    private final TransactionTemplate transactionTemplate;

    public AuditRetentionJob(
            AuditLogRepository repository,
            AuditRetentionProperties properties,
            PlatformTransactionManager transactionManager,
            @Value("${ctt.audit.retention.sweep-interval-ms:3600000}") long sweepIntervalMs,
            @Value("${ctt.audit.retention.initial-delay-ms:3600000}") long initialDelayMs) {
        if (sweepIntervalMs <= 0 || initialDelayMs < 0) {
            throw new IllegalArgumentException(
                    "Audit retention schedule intervals must be non-negative and the sweep interval positive");
        }
        this.repository = repository;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Deletes audit rows older than the configured retention period, if one is set. */
    @Scheduled(
            fixedDelayString = "${ctt.audit.retention.sweep-interval-ms:3600000}",
            initialDelayString = "${ctt.audit.retention.initial-delay-ms:3600000}")
    public void purgeExpired() {
        int days = properties.days();
        if (days <= 0) {
            return;
        }

        Instant cutoff = Instant.now().minus(Duration.ofDays(days));

        int total = 0;
        Integer deleted;
        do {
            deleted =
                    transactionTemplate.execute(
                            status -> repository.deleteBatchBefore(cutoff, properties.batchSize()));
            total += deleted == null ? 0 : deleted;
        } while (deleted != null && deleted == properties.batchSize());

        if (total > 0) {
            log.info("Purged {} audit rows older than {} days", total, days);
        }
    }
}
