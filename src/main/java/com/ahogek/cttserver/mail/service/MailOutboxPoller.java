package com.ahogek.cttserver.mail.service;

import com.ahogek.cttserver.common.config.properties.CttMailProperties;
import com.ahogek.cttserver.mail.entity.MailOutbox;
import com.ahogek.cttserver.mail.repository.MailOutboxRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled poller for outbox email dispatch.
 *
 * <p>Periodically polls the mail_outbox table for PENDING or retry-eligible FAILED records and
 * delegates to {@link MailOutboxProcessor} for individual message handling.
 *
 * <p>Design characteristics:
 *
 * <ul>
 *   <li>Fixed-delay scheduling prevents overlapping executions
 *   <li>Batch fetching with configurable size
 *   <li>Each message processed in isolated transaction (via processor)
 *   <li>Optimistic locking prevents duplicate sends across multiple nodes
 * </ul>
 *
 * @author AhogeK
 * @since 2026-03-20
 * @see MailOutboxProcessor
 * @see MailOutboxRepository#findPendingJobs
 */
@Component
public class MailOutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(MailOutboxPoller.class);

    private final MailOutboxRepository outboxRepository;
    private final MailOutboxProcessor outboxProcessor;
    private final CttMailProperties mailProperties;

    public MailOutboxPoller(
            MailOutboxRepository outboxRepository,
            MailOutboxProcessor outboxProcessor,
            CttMailProperties mailProperties) {
        this.outboxRepository = outboxRepository;
        this.outboxProcessor = outboxProcessor;
        this.mailProperties = mailProperties;
    }

    /**
     * Polls for pending emails and dispatches them.
     *
     * <p>Scheduled with fixed delay to prevent overlapping executions. Fetches a batch of pending
     * messages and processes each one individually. Failures in individual messages don't affect
     * other messages in the batch due to REQUIRES_NEW transactions.
     */
    @Scheduled(fixedDelayString = "${ctt.mail.outbox.poll-interval-ms:5000}")
    public void pollAndDispatch() {
        int batchSize = mailProperties.outbox().batchSize();
        Instant now = Instant.now();

        Pageable pageable = PageRequest.of(0, batchSize, Sort.by("createdAt").ascending());
        List<MailOutbox> pendingJobs = outboxRepository.findPendingJobs(now, pageable);

        if (pendingJobs.isEmpty()) {
            return;
        }

        log.debug("Found {} pending emails to dispatch", pendingJobs.size());

        int successCount = 0;
        int failureCount = 0;

        for (MailOutbox outbox : pendingJobs) {
            try {
                outboxProcessor.processSingleMessage(outbox);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("Unexpected error processing email [id={}]", outbox.getId(), e);
            }
        }

        log.info("Outbox poll completed: {} processed, {} failed", successCount, failureCount);
    }
}
