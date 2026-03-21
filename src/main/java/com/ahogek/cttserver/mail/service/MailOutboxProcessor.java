package com.ahogek.cttserver.mail.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.model.AuditDetails;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.mail.dispatch.ExponentialBackoffRetryStrategy;
import com.ahogek.cttserver.mail.dispatch.MailDispatcher;
import com.ahogek.cttserver.mail.entity.MailOutbox;
import com.ahogek.cttserver.mail.enums.MailOutboxStatus;
import com.ahogek.cttserver.mail.repository.MailOutboxRepository;

import jakarta.mail.MessagingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Single-message processor for outbox email delivery.
 *
 * <p>Handles the complete lifecycle of a single email delivery attempt:
 *
 * <ul>
 *   <li>Optimistic locking via {@code @Version} to prevent duplicate sends
 *   <li>State transitions: PENDING → SENDING → SENT/FAILED
 *   <li>Exponential backoff retry scheduling with jitter
 *   <li>Audit event publishing for delivery tracking
 * </ul>
 *
 * <p>Transaction isolation: Each message is processed in a separate transaction ({@code
 * REQUIRES_NEW}) so that failures don't rollback the entire batch.
 *
 * @author AhogeK
 * @since 2026-03-20
 * @see MailOutboxPoller
 * @see MailDispatcher
 * @see ExponentialBackoffRetryStrategy
 */
@Service
public class MailOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(MailOutboxProcessor.class);

    private static final String KEY_RECIPIENT = "recipient";
    private static final String KEY_SUBJECT = "subject";
    private static final String KEY_BIZ_TYPE = "bizType";
    private static final String KEY_TRACE_ID = "traceId";
    private static final String VALUE_NA = "N/A";

    private final MailOutboxRepository outboxRepository;
    private final MailDispatcher mailDispatcher;
    private final AuditLogService auditLogService;
    private final ExponentialBackoffRetryStrategy retryStrategy;

    public MailOutboxProcessor(
            MailOutboxRepository outboxRepository,
            MailDispatcher mailDispatcher,
            AuditLogService auditLogService,
            ExponentialBackoffRetryStrategy retryStrategy) {
        this.outboxRepository = outboxRepository;
        this.mailDispatcher = mailDispatcher;
        this.auditLogService = auditLogService;
        this.retryStrategy = retryStrategy;
    }

    /**
     * Processes a single outbox message with isolated transaction.
     *
     * <p>Workflow:
     *
     * <ol>
     *   <li>Mark message as SENDING (optimistic lock check)
     *   <li>Dispatch via SMTP
     *   <li>On success: mark SENT, publish MAIL_SENT audit
     *   <li>On failure: calculate next retry, mark FAILED, publish MAIL_DELIVERY_FAILED audit
     *   <li>If retries exhausted: mark CANCELLED, publish MAIL_DELIVERY_EXHAUSTED audit
     * </ol>
     *
     * <p>Optimistic locking failures are silently ignored (another node processed it).
     *
     * @param outbox the outbox entry to process
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingleMessage(MailOutbox outbox) {
        UUID messageId = outbox.getId();

        try {
            // Optimistic lock: mark as SENDING
            outbox.markSending();
            outboxRepository.saveAndFlush(outbox);

            log.debug(
                    "Processing email [id={}, traceId={}] to {}",
                    messageId,
                    outbox.getTraceId(),
                    outbox.getRecipient());

            // Attempt SMTP delivery
            mailDispatcher.dispatch(outbox);

            // Success: mark as SENT
            outbox.markSent();
            outboxRepository.save(outbox);

            log.info("Email sent successfully [id={}] to {}", messageId, outbox.getRecipient());

            // Publish audit event
            publishMailSentAudit(outbox);

        } catch (OptimisticLockingFailureException _) {
            // Another node is processing this message - skip silently
            log.debug("Optimistic lock conflict for email [id={}], skipping", messageId);

        } catch (MessagingException | UnsupportedEncodingException e) {
            // Delivery failure - schedule retry
            handleDeliveryFailure(outbox, e.getMessage());
        }
    }

    /**
     * Handles delivery failure with exponential backoff retry.
     *
     * <p>Calculates next retry time using exponential backoff formula: {@code delay = min(base *
     * multiplier^(attempt-1), maxDelay)}
     *
     * @param outbox the failed message
     * @param errorMessage the error from the failed attempt
     */
    private void handleDeliveryFailure(MailOutbox outbox, String errorMessage) {
        UUID messageId = outbox.getId();
        int currentAttempt = outbox.getRetryCount();

        Instant nextRetryAt = retryStrategy.calculateNextRetryTime(currentAttempt);
        outbox.markFailed(errorMessage, nextRetryAt);
        outboxRepository.save(outbox);

        if (outbox.getStatus() == MailOutboxStatus.CANCELLED) {
            log.warn(
                    "Email delivery exhausted max retries [id={}] to {} after {} attempts",
                    messageId,
                    outbox.getRecipient(),
                    outbox.getRetryCount());

            publishMailDeliveryExhaustedAudit(outbox, errorMessage);
        } else {
            log.warn(
                    "Email delivery failed [id={}] to {}, retry {}/{} scheduled at {}",
                    messageId,
                    outbox.getRecipient(),
                    outbox.getRetryCount(),
                    outbox.getMaxRetries(),
                    nextRetryAt);

            publishMailDeliveryFailedAudit(outbox, errorMessage);
        }
    }

    /**
     * Publishes MAIL_SENT audit event.
     *
     * @param outbox the successfully sent message
     */
    private void publishMailSentAudit(MailOutbox outbox) {
        AuditDetails details =
                AuditDetails.extension(
                        Map.of(
                                KEY_RECIPIENT, outbox.getRecipient(),
                                KEY_SUBJECT, outbox.getSubject(),
                                KEY_BIZ_TYPE, outbox.getBizType(),
                                KEY_TRACE_ID,
                                        outbox.getTraceId() != null
                                                ? outbox.getTraceId()
                                                : VALUE_NA));

        auditLogService.log(
                outbox.getBizId(),
                AuditAction.MAIL_SENT,
                ResourceType.MAIL_OUTBOX,
                outbox.getId().toString(),
                SecuritySeverity.INFO,
                details);
    }

    /**
     * Publishes MAIL_DELIVERY_FAILED audit event.
     *
     * @param outbox the failed message
     * @param errorMessage the failure reason
     */
    private void publishMailDeliveryFailedAudit(MailOutbox outbox, String errorMessage) {
        AuditDetails details =
                AuditDetails.extension(
                        Map.of(
                                KEY_RECIPIENT,
                                outbox.getRecipient(),
                                KEY_SUBJECT,
                                outbox.getSubject(),
                                KEY_BIZ_TYPE,
                                outbox.getBizType(),
                                "retryCount",
                                outbox.getRetryCount(),
                                "maxRetries",
                                outbox.getMaxRetries(),
                                "nextRetryAt",
                                outbox.getNextRetryAt().toString(),
                                "error",
                                errorMessage,
                                KEY_TRACE_ID,
                                outbox.getTraceId() != null ? outbox.getTraceId() : VALUE_NA));

        auditLogService.log(
                outbox.getBizId(),
                AuditAction.MAIL_DELIVERY_FAILED,
                ResourceType.MAIL_OUTBOX,
                outbox.getId().toString(),
                SecuritySeverity.WARNING,
                details);
    }

    /**
     * Publishes MAIL_DELIVERY_EXHAUSTED audit event.
     *
     * @param outbox the exhausted message
     * @param errorMessage the final failure reason
     */
    private void publishMailDeliveryExhaustedAudit(MailOutbox outbox, String errorMessage) {
        AuditDetails details =
                AuditDetails.extension(
                        Map.of(
                                KEY_RECIPIENT,
                                outbox.getRecipient(),
                                KEY_SUBJECT,
                                outbox.getSubject(),
                                KEY_BIZ_TYPE,
                                outbox.getBizType(),
                                "totalAttempts",
                                outbox.getRetryCount(),
                                "finalError",
                                errorMessage,
                                KEY_TRACE_ID,
                                outbox.getTraceId() != null ? outbox.getTraceId() : VALUE_NA));

        auditLogService.log(
                outbox.getBizId(),
                AuditAction.MAIL_DELIVERY_EXHAUSTED,
                ResourceType.MAIL_OUTBOX,
                outbox.getId().toString(),
                SecuritySeverity.CRITICAL,
                details);
    }
}
