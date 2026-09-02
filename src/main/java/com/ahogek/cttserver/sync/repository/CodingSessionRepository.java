package com.ahogek.cttserver.sync.repository;

import com.ahogek.cttserver.sync.entity.CodingSession;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link CodingSession} aggregate root operations.
 *
 * <p>All read paths exclude soft-deleted sessions ({@code is_deleted = false}) so that deleted
 * sessions disappear from every query surface while remaining physically present for the change
 * log. Lookup paths leverage the PostgreSQL indexes defined in the {@code
 * V20260303210000__init_base_schema.sql} migration:
 *
 * <ul>
 *   <li>{@code uk_coding_sessions_user_session_uuid} — unique constraint on {@code (user_id,
 *       session_uuid)}; backs the client-session lookup.
 *   <li>{@code idx_sessions_user_time} — partial index on {@code (user_id, start_time, end_time)
 *       WHERE is_deleted = FALSE}; backs per-user listing and counting.
 *   <li>{@code idx_sessions_sync_lookup} — b-tree on {@code (user_id, server_version)}; backs the
 *       LWW watermark query.
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-25
 */
@Repository
public interface CodingSessionRepository extends JpaRepository<CodingSession, UUID> {

    /**
     * Finds a live session by owner and client-generated session UUID.
     *
     * <p>Used by the push path to decide between insert and update. The unique constraint {@code
     * uk_coding_sessions_user_session_uuid} guarantees at most one match; the {@code is_deleted}
     * predicate is applied as a residual filter after the unique lookup.
     *
     * @param userId the owning user id
     * @param sessionUuid the client-generated session UUID
     * @return {@code Optional} containing the live session when found
     */
    Optional<CodingSession> findByUserIdAndSessionUuidAndIsDeletedFalse(
            UUID userId, UUID sessionUuid);

    /**
     * Batch-fetches sessions of a user by their client-generated session UUIDs, including
     * soft-deleted rows.
     *
     * <p>Used by the push path to load a whole client batch in one query instead of one SELECT per
     * session. Duplicate {@code sessionUuid} values in the incoming batch are de-duplicated by the
     * caller. The result is unordered; the caller indexes by {@code sessionUuid}.
     *
     * @param userId the owning user id
     * @param sessionUuids the client session UUIDs to load
     * @return matching sessions, including soft-deleted rows; never {@code null}
     */
    List<CodingSession> findAllByUserIdAndSessionUuidIn(UUID userId, Collection<UUID> sessionUuids);

    /**
     * Lists all live sessions owned by a user.
     *
     * <p>Backed by the partial index {@code idx_sessions_user_time}, whose {@code WHERE is_deleted
     * = FALSE} predicate matches this query exactly.
     *
     * @param userId the owning user id
     * @return live sessions of the user; never {@code null}
     */
    List<CodingSession> findAllByUserIdAndIsDeletedFalse(UUID userId);

    /**
     * Lists live sessions of a user last updated by a specific device.
     *
     * <p>Used for per-device isolation views. No dedicated index exists on {@code
     * updated_by_device_id}; the query filters the user's sessions (via {@code
     * idx_sessions_user_time}) by device. If this becomes a hot path, a partial index on {@code
     * (user_id, updated_by_device_id) WHERE is_deleted = FALSE} should be added.
     *
     * @param userId the owning user id
     * @param deviceId the device that last updated the session
     * @return matching live sessions; never {@code null}
     */
    List<CodingSession> findAllByUserIdAndUpdatedByDeviceIdAndIsDeletedFalse(
            UUID userId, UUID deviceId);

    /**
     * Lists live sessions of a user originating from a specific device.
     *
     * <p>Backed by the partial index {@code idx_sessions_user_origin} ({@code (user_id,
     * origin_device_id) WHERE is_deleted = FALSE}), which matches this query exactly.
     *
     * @param userId the owning user id
     * @param originDeviceId the device that originally pushed the session
     * @return matching live sessions; never {@code null}
     */
    List<CodingSession> findAllByUserIdAndOriginDeviceIdAndIsDeletedFalse(
            UUID userId, UUID originDeviceId);

    /**
     * Lists live sessions of a user originating from any of the given devices.
     *
     * <p>Used by the IDE filter, which resolves a device set from the registry before querying.
     * Backed by {@code idx_sessions_user_origin} ({@code (user_id, origin_device_id) WHERE
     * is_deleted = FALSE}) via its user + origin-device prefix.
     *
     * @param userId the owning user id
     * @param originDeviceIds the device set to include
     * @return matching live sessions; never {@code null}
     */
    List<CodingSession> findAllByUserIdAndOriginDeviceIdInAndIsDeletedFalse(
            UUID userId, Collection<UUID> originDeviceIds);

    /**
     * Lists the distinct calendar years that contain at least one valid coding session.
     *
     * <p>Only sessions with a positive duration count ({@code start_time < end_time}), matching the
     * StatsCalculator validity rule, so the year list never diverges from the aggregation
     * dimension. Backed by the partial index {@code idx_sessions_user_time}.
     *
     * @param userId the owning user
     * @return distinct years of the user's valid sessions, unordered
     */
    @Query(
            "SELECT DISTINCT EXTRACT(YEAR FROM s.startTime) FROM CodingSession s "
                    + "WHERE s.userId = :userId AND s.isDeleted = false "
                    + "AND s.startTime < s.endTime")
    List<Integer> findDistinctYearsByUserIdAndIsDeletedFalse(@Param("userId") UUID userId);

    /**
     * Counts live sessions owned by a user.
     *
     * <p>Backed by the partial index {@code idx_sessions_user_time} via an index-only scan.
     *
     * @param userId the owning user id
     * @return number of live sessions for the user
     */
    long countByUserIdAndIsDeletedFalse(UUID userId);

    /**
     * Batch-fetches live sessions by their primary keys.
     *
     * <p>Used by the push path to load the sessions referenced by an incoming batch without an N+1
     * lookup. Each id resolves through the primary key index.
     *
     * @param ids session primary keys to load
     * @return the live sessions among {@code ids}; never {@code null}
     */
    @Query("SELECT s FROM CodingSession s WHERE s.id IN :ids AND s.isDeleted = false")
    List<CodingSession> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    /**
     * Batch-fetches sessions by their primary keys, including soft-deleted rows.
     *
     * <p>Used by the pull path to resolve the session snapshots referenced by change-log entries.
     * Unlike {@link #findAllByIdInAndIsDeletedFalse}, this query deliberately includes deleted
     * sessions so that a {@code DELETE} change can still deliver the deleted row's identity to the
     * client. Each id resolves through the primary key index.
     *
     * @param ids session primary keys to load
     * @return the sessions among {@code ids}, including soft-deleted ones; never {@code null}
     */
    @Query("SELECT s FROM CodingSession s WHERE s.id IN :ids")
    List<CodingSession> findAllByIdIn(@Param("ids") Collection<UUID> ids);

    /**
     * Lists live sessions of a user whose server version is above a watermark.
     *
     * <p>Used by the LWW push path to detect sessions that changed on the server after a device's
     * last known version. Backed by {@code idx_sessions_sync_lookup} on {@code (user_id,
     * server_version)}; the {@code is_deleted} predicate is applied as a residual filter.
     *
     * @param userId the owning user id
     * @param serverVersion the exclusive lower bound on server version
     * @return matching live sessions; never {@code null}
     */
    List<CodingSession> findAllByUserIdAndServerVersionGreaterThanAndIsDeletedFalse(
            UUID userId, long serverVersion);

    /**
     * Lists live sessions of a user that overlap the half-open instant range {@code [start,
     * endExclusive)}.
     *
     * <p>Used by the materialization recompute: the caller passes the instant bounds of the touched
     * UTC days (midnight to midnight), so only sessions overlapping those days are loaded and the
     * recompute cost is bounded by the range instead of the whole history. Backed by {@code
     * idx_sessions_user_time} (partial index on user + time range where not deleted).
     *
     * @param userId the owning user id
     * @param start first instant (inclusive)
     * @param endExclusive instant just after the last moment (exclusive)
     * @return the overlapping live sessions; never {@code null}
     */
    @Query(
            "SELECT s FROM CodingSession s WHERE s.userId = :userId AND s.isDeleted = false "
                    + "AND s.startTime < :endExclusive AND s.endTime >= :start")
    List<CodingSession> findLiveInUtcDayRange(
            @Param("userId") UUID userId,
            @Param("start") Instant start,
            @Param("endExclusive") Instant endExclusive);
}
