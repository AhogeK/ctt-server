package com.ahogek.cttserver.audit.enums;

/**
 * Security severity levels for audit events.
 *
 * <p>Used for alerting and prioritization in observability systems.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
public enum SecuritySeverity {
    INFO, // Normal business audit (e.g., login success)
    WARNING, // Potential risk (e.g., wrong password, rate limit triggered)
    CRITICAL // Clear security attack (e.g., privilege escalation, JWT tampering)
}
