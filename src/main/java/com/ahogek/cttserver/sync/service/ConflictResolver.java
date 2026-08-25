package com.ahogek.cttserver.sync.service;

import com.ahogek.cttserver.sync.entity.CodingSession;
import com.ahogek.cttserver.sync.enums.ChangeOp;

/**
 * Decides which of two {@link CodingSession} states wins under last-write-wins (LWW) conflict
 * resolution.
 *
 * <p>Pure domain logic: it reads only the version and delete fields of the two states and returns a
 * {@link Decision}; it never mutates either argument and has no Spring or persistence dependencies.
 * The future push service consumes the decision to route the winning state (apply fields,
 * soft-delete, or keep the server row) and to append the matching change-log entry.
 *
 * <p>Rules are evaluated in priority order:
 *
 * <ol>
 *   <li><b>Delete wins</b> — a soft-deleted state beats a live one; deletion is the strongest
 *       terminal state. When both are deleted the comparison falls through to the version rules,
 *       because a more recent delete of an already-deleted session is still a delete.
 *   <li><b>Server version</b> — when both sides carry a server-assigned version ({@code > 0}), the
 *       higher wins. This applies to replay/merge scenarios where both states were persisted.
 *   <li><b>Client version</b> — when server versions are equal, or either side has no server
 *       version yet (a fresh client submission carries {@code serverVersion == 0}), the higher
 *       client version wins.
 *   <li><b>Client modified at</b> — when client versions are also equal, the later {@code
 *       clientModifiedAt} wins.
 * </ol>
 *
 * <p>Two states that are identical on every compared field resolve to {@link
 * Decision#KEEP_EXISTING} so that re-submitting unchanged state is an idempotent no-op.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-25
 */
public final class ConflictResolver {

    private ConflictResolver() {
        // Static utility; no instances.
    }

    /**
     * Resolves which of the two session states wins under LWW semantics.
     *
     * @param existing the server-side state of the session
     * @param incoming the submitted state (client version, modified-at and delete flag populated)
     * @return the {@link Decision} describing how the caller should route the conflict
     */
    public static Decision resolve(CodingSession existing, CodingSession incoming) {
        if (existing.isDeleted() != incoming.isDeleted()) {
            return existing.isDeleted() ? Decision.KEEP_EXISTING : Decision.APPLY_DELETE;
        }

        boolean existingHasServerVersion = existing.getServerVersion() > 0;
        boolean incomingHasServerVersion = incoming.getServerVersion() > 0;
        if (existingHasServerVersion
                && incomingHasServerVersion
                && existing.getServerVersion() != incoming.getServerVersion()) {
            return existing.getServerVersion() > incoming.getServerVersion()
                    ? Decision.KEEP_EXISTING
                    : decisionFor(incoming);
        }

        if (existing.getClientVersion() != incoming.getClientVersion()) {
            return existing.getClientVersion() > incoming.getClientVersion()
                    ? Decision.KEEP_EXISTING
                    : decisionFor(incoming);
        }

        int modifiedAtComparison =
                existing.getClientModifiedAt().compareTo(incoming.getClientModifiedAt());
        if (modifiedAtComparison != 0) {
            return modifiedAtComparison > 0 ? Decision.KEEP_EXISTING : decisionFor(incoming);
        }

        // Identical states: keep the server row so a re-submission is an idempotent no-op.
        return Decision.KEEP_EXISTING;
    }

    private static Decision decisionFor(CodingSession incoming) {
        return incoming.isDeleted() ? Decision.APPLY_DELETE : Decision.APPLY_INCOMING;
    }

    /**
     * Outcome of an LWW conflict resolution.
     *
     * <p>Callers route on this value: {@link #APPLY_INCOMING} applies the submitted live state and
     * appends a {@link ChangeOp#UPSERT} change-log entry, {@link #APPLY_DELETE} soft-deletes the
     * server row and appends a {@link ChangeOp#DELETE} entry, and {@link #KEEP_EXISTING} leaves the
     * server row untouched.
     */
    public enum Decision {
        /** The submitted live state wins; apply its fields and log an upsert. */
        APPLY_INCOMING,

        /** The server-side state wins; leave the persisted row untouched. */
        KEEP_EXISTING,

        /** The submitted delete wins; soft-delete the server row and log a delete. */
        APPLY_DELETE
    }
}
