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
 * <p><b>Content identity</b> — before the version rules run, two live states whose business fields
 * ({@code projectName}, {@code language}, {@code startTime}, {@code endTime}) are identical resolve
 * to {@link Decision#SKIP_UNCHANGED}. This makes a re-push of unchanged content an idempotent no-op
 * even when the version/modified-at metadata drifted (e.g. plugin full re-push with timezone or
 * clock precision), mirroring the plugin's INSERT OR IGNORE import. Deleted states are exempt so
 * delete-version competition keeps its semantics.
 *
 * <p>Every other rule can return a state whose content differs from the server's, so those are
 * conflicts even when the server wins: a stale client and a lost version race both land on {@link
 * Decision#KEEP_EXISTING}, and the caller reports them as rejected attempts rather than as
 * idempotent no-ops.
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

        // Content-identical live states are idempotent re-submissions: no business change to
        // apply, so keep the server row and emit no change-log entry even if the metadata
        // (clientVersion / clientModifiedAt) drifted. Tombstones skip this so delete-version
        // competition keeps its own rules.
        if (!existing.isDeleted() && sameContent(existing, incoming)) {
            return Decision.SKIP_UNCHANGED;
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
        // Same metadata on both sides but different content: neither side is newer, so the server
        // row stays and the client's diverging state is dropped as a conflict.
        return Decision.KEEP_EXISTING;
    }

    private static boolean sameContent(CodingSession existing, CodingSession incoming) {
        return existing.getProjectName().equals(incoming.getProjectName())
                && existing.getLanguage().equals(incoming.getLanguage())
                && existing.getStartTime().equals(incoming.getStartTime())
                && existing.getEndTime().equals(incoming.getEndTime());
    }

    private static Decision decisionFor(CodingSession incoming) {
        return incoming.isDeleted() ? Decision.APPLY_DELETE : Decision.APPLY_INCOMING;
    }

    /**
     * Outcome of an LWW conflict resolution.
     *
     * <p>Callers route on this value: {@link #APPLY_INCOMING} applies the submitted live state and
     * appends a {@link ChangeOp#UPSERT} change-log entry, {@link #APPLY_DELETE} soft-deletes the
     * server row and appends a {@link ChangeOp#DELETE} entry, and both {@link #KEEP_EXISTING} and
     * {@link #SKIP_UNCHANGED} leave the server row untouched — the first because the client lost,
     * the second because there was nothing to apply.
     */
    public enum Decision {
        /** The submitted live state wins; apply its fields and log an upsert. */
        APPLY_INCOMING,

        /**
         * The server-side state wins over a state that differs from it; leave the persisted row
         * untouched. This is a rejected client attempt — a stale version or a lost race — and not
         * an idempotent re-submission.
         */
        KEEP_EXISTING,

        /**
         * The submitted state carries no business change, so the row is left untouched and nothing
         * is logged. Re-submitting unchanged content is the normal case for a client that re-pushes
         * its full session list.
         */
        SKIP_UNCHANGED,

        /** The submitted delete wins; soft-delete the server row and log a delete. */
        APPLY_DELETE
    }
}
