package com.ahogek.cttserver.sync.repository;

import com.ahogek.cttserver.sync.entity.SessionChange;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the {@link SessionChange} change log.
 *
 * <p>The change log is the backbone of incremental pull: every accepted write appends a row with a
 * monotonically increasing {@code change_id} (BIGSERIAL), so a device can pull everything after its
 * stored watermark. Queries leverage the PostgreSQL indexes defined in the {@code
 * V20260303210000__init_base_schema.sql} migration:
 *
 * <ul>
 *   <li>{@code idx_session_changes_user_change} — b-tree on {@code (user_id, change_id)}; backs the
 *       incremental pull, the has-more count and the per-user max watermark.
 *   <li>{@code idx_session_changes_session_id} — b-tree on {@code session_id}; backs per-session
 *       history.
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-25
 */
@Repository
public interface SessionChangeRepository extends JpaRepository<SessionChange, Long> {

    /**
     * Pulls the changes of a user that occurred after a watermark, in change order.
     *
     * <p>The core incremental pull query: {@code change_id > cursor} with per-user isolation,
     * ordered ascending so the caller can page deterministically and advance the cursor to the last
     * returned id. Backed by {@code idx_session_changes_user_change}.
     *
     * @param cursor the exclusive lower bound on change id (the device's watermark)
     * @param userId the owning user id
     * @return matching changes in ascending change-id order; never {@code null}
     */
    List<SessionChange> findAllByChangeIdGreaterThanAndUserIdOrderByChangeIdAsc(
            long cursor, UUID userId);

    /**
     * Pulls at most {@code limit} changes of a user after a watermark, in change order.
     *
     * <p>Paged variant of the incremental pull: the caller fetches {@code limit + 1} rows to detect
     * a next page and trims the extra row, so one query answers both "the page" and "is there
     * more". Backed by {@code idx_session_changes_user_change}.
     *
     * @param cursor the exclusive lower bound on change id (the device's watermark)
     * @param userId the owning user id
     * @param limit the maximum number of changes to return
     * @return up to {@code limit} matching changes in ascending change-id order; never {@code null}
     */
    List<SessionChange> findAllByChangeIdGreaterThanAndUserIdOrderByChangeIdAsc(
            long cursor, UUID userId, Limit limit);

    /**
     * Lists the full change history of a single session for a user.
     *
     * <p>Used for per-session audit or replay. Backed by {@code idx_session_changes_session_id};
     * the {@code user_id} predicate is applied as a residual filter.
     *
     * @param userId the owning user id
     * @param sessionId the affected session id
     * @return the session's changes in ascending change-id order; never {@code null}
     */
    List<SessionChange> findAllByUserIdAndSessionIdOrderByChangeIdAsc(UUID userId, UUID sessionId);

    /**
     * Counts the changes of a user that occurred after a watermark.
     *
     * <p>Used as the has-more check when paging a pull. Backed by {@code
     * idx_session_changes_user_change}.
     *
     * @param cursor the exclusive lower bound on change id
     * @param userId the owning user id
     * @return number of pending changes for the user
     */
    long countByChangeIdGreaterThanAndUserId(long cursor, UUID userId);

    /**
     * Returns the highest change id recorded for a user, or zero when none exists.
     *
     * <p>Used to initialize or validate a device's pull cursor. The {@code COALESCE} keeps the
     * result well-defined for users with no changes. Backed by {@code
     * idx_session_changes_user_change} via an index-only scan for the user's last entry.
     *
     * @param userId the owning user id
     * @return the user's max change id, or {@code 0} when the user has no changes
     */
    @Query("SELECT COALESCE(MAX(c.changeId), 0) FROM SessionChange c WHERE c.userId = :userId")
    long findMaxChangeIdForUser(@Param("userId") UUID userId);
}
