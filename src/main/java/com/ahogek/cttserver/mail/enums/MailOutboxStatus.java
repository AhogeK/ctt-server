package com.ahogek.cttserver.mail.enums;

/**
 * Delivery status for {@link com.ahogek.cttserver.mail.entity.MailOutbox} entries.
 *
 * <p>State machine:
 *
 * <pre>
 *   PENDING ──► SENDING ──► SENT
 *      ▲             │
 *      │         FAILED ──► CANCELLED (retry exhausted)
 *      └─────────────┘ (next_retry_at reached)
 *
 *   Any state ──► CANCELLED (manual)
 * </pre>
 *
 * @author AhogeK
 * @since 2026-03-18
 */
public enum MailOutboxStatus {

    /** Awaiting delivery. */
    PENDING("Pending delivery"),

    /** Currently being sent — optimistic placeholder to prevent duplicate sends. */
    SENDING("Sending"),

    /** Successfully delivered. */
    SENT("Delivered"),

    /** Delivery failed; scheduled for retry. */
    FAILED("Delivery failed"),

    /** Canceled — manually or after exhausting all retries. */
    CANCELLED("Cancelled");

    private final String description;

    MailOutboxStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** Whether this status represents a terminal (non-retryable) state. */
    public boolean isTerminal() {
        return this == SENT || this == CANCELLED;
    }
}
