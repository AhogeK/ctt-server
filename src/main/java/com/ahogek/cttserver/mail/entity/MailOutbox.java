package com.ahogek.cttserver.mail.entity;

import com.ahogek.cttserver.mail.enums.MailOutboxStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Transactional outbox entity for asynchronous email delivery.
 *
 * <p>Strategy: <b>pre-rendered storage</b> — {@code bodyHtml} and {@code bodyText} hold the fully
 * rendered content used during delivery; {@code templateCode} and {@code payload} are retained for
 * audit and replay traceability.
 *
 * <p>Concurrency: {@code @Version} provides optimistic locking so that only one scheduler node
 * transitions a record from {@code PENDING → SENDING} at a time.
 *
 * @author AhogeK
 * @since 2026-03-18
 */
@Entity
@Table(name = "mail_outbox")
@EntityListeners(AuditingEntityListener.class)
public class MailOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Optimistic lock — prevents duplicate sends under concurrent scheduler nodes. */
    @Version private Long version;

    /** Business category, e.g. {@code REGISTER_VERIFY}, {@code RESET_PASSWORD}. */
    @Column(nullable = false, length = 32)
    private String bizType;

    /** Optional reference to the business entity that triggered this email. */
    private UUID bizId;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String subject;

    /** Pre-rendered HTML body; used directly during SMTP delivery. */
    @Column(columnDefinition = "TEXT")
    private String bodyHtml;

    /** Pre-rendered plain-text fallback body. */
    @Column(columnDefinition = "TEXT")
    private String bodyText;

    /** Template identifier retained for audit/replay; not used at delivery time. */
    @Column(length = 64)
    private String templateCode;

    /** Template rendering payload retained for audit/replay. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MailOutboxStatus status = MailOutboxStatus.PENDING;

    @Column(nullable = false)
    private int retryCount = 0;

    /** Hard ceiling on delivery attempts; auto-cancels when exceeded. */
    @Column(nullable = false)
    private int maxRetries = 3;

    /** Timestamp after which a FAILED record becomes eligible for re-dispatch. */
    private Instant nextRetryAt;

    /** Populated when status transitions to SENT. */
    private Instant sentAt;

    /** Last SMTP or rendering error message; overwritten on each attempt. */
    @Column(columnDefinition = "TEXT")
    private String lastError;

    /** OpenTelemetry trace ID for end-to-end delivery observability. */
    @Column(length = 64)
    private String traceId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    /** Required no-args constructor for JPA. */
    public MailOutbox() {}

    // -------------------------------------------------------------------------
    // State-machine methods — always mutate status through these, never setters
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when another delivery attempt is permissible. Evaluated <em>before</em>
     * incrementing {@code retryCount}.
     */
    public boolean canRetry() {
        return !status.isTerminal() && retryCount < maxRetries;
    }

    /** Transitions {@code PENDING → SENDING}. Guarded by optimistic lock at the DB level. */
    public void markSending() {
        this.status = MailOutboxStatus.SENDING;
    }

    /** Transitions {@code SENDING → SENT} and records delivery time. */
    public void markSent() {
        this.status = MailOutboxStatus.SENT;
        this.sentAt = Instant.now();
    }

    /**
     * Handles a delivery failure.
     *
     * <ul>
     *   <li>Increments {@code retryCount} unconditionally.
     *   <li>If retries remain: sets {@code FAILED} and schedules {@code nextRetryAt}.
     *   <li>If retries are exhausted: auto-cancels via {@link #cancel()}.
     * </ul>
     *
     * @param error the error message from the failed attempt
     * @param nextRetry the earliest time the scheduler should re-attempt delivery
     */
    public void markFailed(String error, Instant nextRetry) {
        this.retryCount++;
        this.lastError = error;
        if (canRetry()) {
            this.status = MailOutboxStatus.FAILED;
            this.nextRetryAt = nextRetry;
        } else {
            cancel();
        }
    }

    /**
     * Transitions to {@code CANCELLED} from any non-terminal state.
     *
     * @throws IllegalStateException if the current status is already terminal (SENT or CANCELLED)
     */
    public void cancel() {
        if (status.isTerminal()) {
            throw new IllegalStateException("Cannot cancel a terminal state: " + status);
        }
        this.status = MailOutboxStatus.CANCELLED;
    }

    // -------------------------------------------------------------------------
    // Getters & Setters
    // -------------------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public UUID getBizId() {
        return bizId;
    }

    public void setBizId(UUID bizId) {
        this.bizId = bizId;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBodyHtml() {
        return bodyHtml;
    }

    public void setBodyHtml(String bodyHtml) {
        this.bodyHtml = bodyHtml;
    }

    public String getBodyText() {
        return bodyText;
    }

    public void setBodyText(String bodyText) {
        this.bodyText = bodyText;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = (payload != null) ? payload : new HashMap<>();
    }

    public MailOutboxStatus getStatus() {
        return status;
    }

    public void setStatus(MailOutboxStatus status) {
        this.status = status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
