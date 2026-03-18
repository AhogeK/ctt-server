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
 * Mail outbox entity for asynchronous email delivery.
 *
 * <p>Uses pre-rendered storage strategy: {@code bodyHtml} and {@code bodyText} contain the fully
 * rendered content ready for delivery, while {@code templateCode} and {@code payload} are retained
 * for audit traceability.
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

    @Column(nullable = false, length = 32)
    private String bizType;

    private UUID bizId;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String bodyHtml;

    @Column(columnDefinition = "TEXT")
    private String bodyText;

    @Column(length = 64)
    private String templateCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MailOutboxStatus status = MailOutboxStatus.PENDING;

    @Column(nullable = false)
    private int retryCount = 0;

    @Column(nullable = false)
    private int maxRetries = 3;

    private Instant nextRetryAt;

    private Instant sentAt;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Column(length = 64)
    private String traceId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    // --- Default constructor for JPA ---
    public MailOutbox() {}

    // --- Business logic methods (Entity state machine) ---

    /**
     * Checks if this mail entry can be retried.
     *
     * @return true if retry count is below max and status is not CANCELLED
     */
    public boolean canRetry() {
        return retryCount < maxRetries && status != MailOutboxStatus.CANCELLED;
    }

    /** Marks this mail as being sent. */
    public void markSending() {
        this.status = MailOutboxStatus.SENDING;
    }

    /** Marks this mail as successfully sent. */
    public void markSent() {
        this.status = MailOutboxStatus.SENT;
        this.sentAt = Instant.now();
    }

    /**
     * Marks this mail as failed with an error message and next retry time.
     *
     * @param error the error message
     * @param nextRetry the scheduled next retry time
     */
    public void markFailed(String error, Instant nextRetry) {
        this.retryCount++;
        this.lastError = error;
        if (canRetry()) {
            this.status = MailOutboxStatus.FAILED;
            this.nextRetryAt = nextRetry;
        } else {
            this.status = MailOutboxStatus.CANCELLED;
        }
    }

    /** Cancels this mail entry. */
    public void cancel() {
        this.status = MailOutboxStatus.CANCELLED;
    }

    // --- Getters and Setters ---

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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
        this.payload = payload;
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
