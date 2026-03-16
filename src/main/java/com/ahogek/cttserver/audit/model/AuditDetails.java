package com.ahogek.cttserver.audit.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standardized audit log details carrier for JSONB storage.
 *
 * <p>Provides strongly-typed, schema-on-write structure to prevent key name chaos in
 * Elasticsearch/PostgreSQL indices.
 *
 * <p>Fields are automatically excluded when null via {@code @JsonInclude}.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditDetails(
        String reason,
        String errorCode,
        Integer attemptCount,
        String stateBefore,
        String stateAfter,
        Map<String, Object> ext) {

    /**
     * Creates empty details with all fields null.
     *
     * @return empty AuditDetails instance
     */
    public static AuditDetails empty() {
        return new AuditDetails(null, null, null, null, null, null);
    }

    /**
     * Creates details with reason field.
     *
     * @param reason the operation rejection or failure reason
     * @return AuditDetails with reason set
     */
    public static AuditDetails reason(String reason) {
        return new AuditDetails(reason, null, null, null, null, null);
    }

    /**
     * Creates details with error code and reason.
     *
     * @param errorCode the internal error code (e.g., "AUTH_001")
     * @param reason the failure description
     * @return AuditDetails with error info
     */
    public static AuditDetails error(String errorCode, String reason) {
        return new AuditDetails(reason, errorCode, null, null, null, null);
    }

    /**
     * Creates details for retry/attempt scenarios.
     *
     * @param attemptCount the counter value
     * @param reason the attempt description
     * @return AuditDetails with attempt count
     */
    public static AuditDetails attempt(int attemptCount, String reason) {
        return new AuditDetails(reason, null, attemptCount, null, null, null);
    }

    /**
     * Creates details for state transition scenarios.
     *
     * @param stateBefore the state before change (JSON string or simple value)
     * @param stateAfter the state after change (JSON string or simple value)
     * @return AuditDetails with state transition info
     */
    public static AuditDetails transition(String stateBefore, String stateAfter) {
        return new AuditDetails(null, null, null, stateBefore, stateAfter, null);
    }

    /**
     * Creates details with extension fields for rare edge cases.
     *
     * <p>Use sparingly - prefer typed fields over ext map.
     *
     * @param ext the extension map for custom fields
     * @return AuditDetails with extension map
     */
    public static AuditDetails extension(Map<String, Object> ext) {
        return new AuditDetails(null, null, null, null, null, ext);
    }
}
