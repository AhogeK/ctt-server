package com.ahogek.cttserver.fixtures;

import com.ahogek.cttserver.mail.entity.MailOutbox;
import com.ahogek.cttserver.mail.enums.MailOutboxStatus;

import java.time.Instant;

/**
 * Mail outbox test data factory using Object Mother and Builder patterns.
 *
 * <p>Provides preset outbox entries for common scenarios and a fluent builder for custom
 * construction.
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * // Object Mother - preset states
 * var pending = MailOutboxFixtures.pending().recipient("user@example.com");
 * var retryable = MailOutboxFixtures.retryableFailed().recipient("retry@example.com");
 * var exhausted = MailOutboxFixtures.exhaustedFailed().recipient("exhausted@example.com");
 *
 * // Custom builder
 * var custom = MailOutboxFixtures.builder()
 *         .recipient("custom@example.com")
 *         .bizType("RESET_PASSWORD")
 *         .status(MailOutboxStatus.SENDING)
 *         .build();
 *
 * // Persisted (Repository test)
 * var persisted = PersistedFixtures.mailOutbox(em, MailOutboxFixtures.pending());
 * }</pre>
 *
 * @author AhogeK
 * @since 2026-03-19
 */
public final class MailOutboxFixtures {

    private MailOutboxFixtures() {}

    // ==========================================
    // Object Mother - Preset States
    // ==========================================

    /** A fresh, unsent outbox entry (PENDING status). */
    public static Builder pending() {
        return new Builder()
                .recipient("pending@example.com")
                .bizType("REGISTER_VERIFY")
                .status(MailOutboxStatus.PENDING);
    }

    /** A failed entry still eligible for retry (nextRetryAt in the past). */
    public static Builder retryableFailed() {
        return new Builder()
                .recipient("retry@example.com")
                .bizType("REGISTER_VERIFY")
                .status(MailOutboxStatus.FAILED)
                .retryCount(1)
                .nextRetryAt(Instant.now().minusSeconds(30));
    }

    /** A failed entry whose retry window has not yet elapsed. */
    public static Builder notYetRetryableFailed() {
        return new Builder()
                .recipient("wait@example.com")
                .bizType("REGISTER_VERIFY")
                .status(MailOutboxStatus.FAILED)
                .retryCount(1)
                .nextRetryAt(Instant.now().plusSeconds(300));
    }

    /** A failed entry that has exhausted all retries (retryCount == maxRetries). */
    public static Builder exhaustedFailed() {
        return new Builder()
                .recipient("exhausted@example.com")
                .bizType("REGISTER_VERIFY")
                .status(MailOutboxStatus.FAILED)
                .retryCount(3)
                .maxRetries(3)
                .nextRetryAt(Instant.now().minusSeconds(10));
    }

    /** An entry currently in transit (SENDING status). */
    public static Builder sending() {
        return new Builder()
                .recipient("sending@example.com")
                .bizType("REGISTER_VERIFY")
                .status(MailOutboxStatus.SENDING);
    }

    /** A successfully delivered entry (SENT status). */
    public static Builder sent() {
        return new Builder()
                .recipient("sent@example.com")
                .bizType("REGISTER_VERIFY")
                .status(MailOutboxStatus.SENT);
    }

    /** A canceled entry. */
    public static Builder cancelled() {
        return new Builder()
                .recipient("cancelled@example.com")
                .bizType("REGISTER_VERIFY")
                .status(MailOutboxStatus.CANCELLED);
    }

    // ==========================================
    // Builder
    // ==========================================

    /** Creates a new builder for custom mail outbox construction. */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for MailOutbox entity. */
    public static final class Builder {
        private String recipient = "default@example.com";
        private String subject = "Test Subject";
        private String bodyHtml = "<p>Test</p>";
        private String bodyText = "Test";
        private String bizType = "TEST_TYPE";
        private String templateCode = "TEST_TEMPLATE";
        private String traceId;
        private MailOutboxStatus status = MailOutboxStatus.PENDING;
        private int retryCount = 0;
        private int maxRetries = 3;
        private Instant nextRetryAt;

        private Builder() {}

        public Builder recipient(String recipient) {
            this.recipient = recipient;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder bodyHtml(String bodyHtml) {
            this.bodyHtml = bodyHtml;
            return this;
        }

        public Builder bodyText(String bodyText) {
            this.bodyText = bodyText;
            return this;
        }

        public Builder bizType(String bizType) {
            this.bizType = bizType;
            return this;
        }

        public Builder templateCode(String templateCode) {
            this.templateCode = templateCode;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder status(MailOutboxStatus status) {
            this.status = status;
            return this;
        }

        public Builder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder nextRetryAt(Instant nextRetryAt) {
            this.nextRetryAt = nextRetryAt;
            return this;
        }

        /**
         * Builds the MailOutbox entity.
         *
         * <p>Note: createdAt and updatedAt are managed by JPA (@CreatedDate/@LastModifiedDate) and
         * will be null until persisted.
         *
         * @return new MailOutbox instance
         */
        public MailOutbox build() {
            var outbox = new MailOutbox();
            outbox.setRecipient(recipient);
            outbox.setSubject(subject);
            outbox.setBodyHtml(bodyHtml);
            outbox.setBodyText(bodyText);
            outbox.setBizType(bizType);
            outbox.setTemplateCode(templateCode);
            outbox.setTraceId(traceId);
            outbox.setStatus(status);
            outbox.setRetryCount(retryCount);
            outbox.setMaxRetries(maxRetries);
            outbox.setNextRetryAt(nextRetryAt);
            return outbox;
        }
    }
}
