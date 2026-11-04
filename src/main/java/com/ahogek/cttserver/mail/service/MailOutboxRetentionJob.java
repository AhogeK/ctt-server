package com.ahogek.cttserver.mail.service;

import com.ahogek.cttserver.mail.enums.MailOutboxStatus;
import com.ahogek.cttserver.mail.repository.MailOutboxRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Bounds how long terminal outbox rows, including rendered verification links, remain in the
 * database.
 *
 * <p>The token itself has a short lifetime (30 minutes to 24 hours); seven days is a safety margin
 * for investigation, not a validity window. Deleting the row also removes the recipient address and
 * display data after delivery has settled.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-04
 */
@Component
public class MailOutboxRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(MailOutboxRetentionJob.class);
    private static final Duration RETENTION = Duration.ofDays(7);
    private static final List<MailOutboxStatus> TERMINAL =
            List.of(MailOutboxStatus.SENT, MailOutboxStatus.CANCELLED);

    private final MailOutboxRepository repository;

    public MailOutboxRetentionJob(MailOutboxRepository repository) {
        this.repository = repository;
    }

    /** Removes terminal rows whose rendered content is older than the retention window. */
    @Scheduled(
            fixedDelayString = "${ctt.mail.outbox.retention-sweep-ms:3600000}",
            initialDelayString = "${ctt.mail.outbox.retention-initial-delay-ms:3600000}")
    @Transactional
    public void purgeExpired() {
        int deleted = repository.deleteTerminalBefore(TERMINAL, Instant.now().minus(RETENTION));
        if (deleted > 0) {
            log.info("Purged {} terminal mail outbox rows older than seven days", deleted);
        }
    }
}
