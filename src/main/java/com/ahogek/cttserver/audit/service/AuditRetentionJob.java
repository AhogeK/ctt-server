package com.ahogek.cttserver.audit.service;

import com.ahogek.cttserver.audit.repository.AuditLogRepository;
import com.ahogek.cttserver.common.config.properties.AuditRetentionProperties;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

/**
 * Enforces the configured audit retention period.
 *
 * <p>Deletion is disabled while {@code ctt.audit.retention.days} is zero. The base profile keeps
 * that default: the period is an operations/compliance decision, and no development or test run
 * should delete security evidence. The production profile sets the approved 180-day period
 * (ADR-0002), so enabling deletion is a versioned, reviewed change rather than an environment
 * accident. Rows are removed in bounded batches so a large table does not become one long lock.
 *
 * <p>A disabled period logs a warning only in the production profile; elsewhere it is expected and
 * logged at info level.
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
    private final Environment environment;

    public AuditRetentionJob(
            AuditLogRepository repository,
            AuditRetentionProperties properties,
            PlatformTransactionManager transactionManager,
            Environment environment,
            @Value("${ctt.audit.retention.sweep-interval-ms:3600000}") long sweepIntervalMs,
            @Value("${ctt.audit.retention.initial-delay-ms:3600000}") long initialDelayMs) {
        if (sweepIntervalMs <= 0 || initialDelayMs < 0) {
            throw new IllegalArgumentException(
                    "Audit retention schedule intervals must be non-negative and the sweep interval positive");
        }
        this.repository = repository;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.environment = environment;
    }

    @PostConstruct
    void logEffectivePolicy() {
        if (properties.days() <= 0) {
            String message =
                    "Audit retention is disabled (ctt.audit.retention.days={}); every audit row is"
                            + " kept";
            if (environment.matchesProfiles("prod")) {
                log.warn(message, properties.days());
            } else {
                log.info(message, properties.days());
            }
            return;
        }
        log.info(
                "Audit retention enabled: deleting rows older than {} days in batches of {}",
                properties.days(),
                properties.batchSize());
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
        long startedAt = System.nanoTime();

        int total = 0;
        try {
            Integer deleted;
            do {
                deleted =
                        transactionTemplate.execute(
                                status ->
                                        repository.deleteBatchBefore(
                                                cutoff, properties.batchSize()));
                int batch = deleted == null ? 0 : deleted;
                total += batch;
                if (batch > 0) {
                    log.debug("Audit retention batch deleted {} rows", batch);
                }
            } while (deleted != null && deleted == properties.batchSize());
        } catch (RuntimeException ex) {
            log.error(
                    "Audit retention sweep failed: cutoff={}, deleted={} rows before the failure",
                    cutoff,
                    total,
                    ex);
            throw ex;
        }

        log.info(
                "Audit retention sweep finished: cutoff={}, deleted={} rows in {} ms",
                cutoff,
                total,
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
    }
}
