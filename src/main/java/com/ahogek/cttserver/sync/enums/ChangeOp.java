package com.ahogek.cttserver.sync.enums;

/**
 * Operation type recorded in the {@code session_changes} change log.
 *
 * <p>Persisted as a string via {@code @Enumerated(EnumType.STRING)} so the stored value must stay
 * in sync with the {@code chk_session_change_op} CHECK constraint in the {@code
 * V20260303210000__init_base_schema.sql} migration ({@code op IN ('UPSERT', 'DELETE')}). Renaming
 * or adding a value here without a matching migration will fail Flyway validation.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-25
 */
public enum ChangeOp {

    /** The session was created or updated (upsert semantics). */
    UPSERT,

    /** The session was soft-deleted. */
    DELETE
}
