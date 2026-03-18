package com.ahogek.cttserver.mail.enums;

/**
 * Delivery status for mail outbox entries.
 *
 * @author AhogeK
 * @since 2026-03-18
 */
public enum MailOutboxStatus {
    /** Awaiting delivery. */
    PENDING,

    /** Currently being sent (optimistic lock placeholder to prevent duplicate sends). */
    SENDING,

    /** Successfully delivered. */
    SENT,

    /** Delivery failed, waiting for retry. */
    FAILED,

    /** Canceled (manually or exceeded retry limit). */
    CANCELLED
}
