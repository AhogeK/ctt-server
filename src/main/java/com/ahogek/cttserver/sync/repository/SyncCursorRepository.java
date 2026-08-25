package com.ahogek.cttserver.sync.repository;

import com.ahogek.cttserver.sync.entity.SyncCursor;
import com.ahogek.cttserver.sync.entity.SyncCursorId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link SyncCursor} per-device watermarks.
 *
 * <p>Each cursor is identified by the composite primary key {@code (user_id, device_id)} declared
 * in the {@code sync_cursors} table of the {@code V20260303210000__init_base_schema.sql} migration;
 * both lookups below resolve through that primary key index.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-25
 */
@Repository
public interface SyncCursorRepository extends JpaRepository<SyncCursor, SyncCursorId> {

    /**
     * Loads the sync cursor of a device for a user.
     *
     * <p>Backed by the composite primary key {@code (user_id, device_id)}. An absent result means
     * the device has never synced; the caller should initialize a fresh cursor at watermark zero.
     *
     * @param userId the owning user id
     * @param deviceId the client device id
     * @return {@code Optional} containing the cursor when found
     */
    Optional<SyncCursor> findByUserIdAndDeviceId(UUID userId, UUID deviceId);

    /**
     * Advances a device's pull watermark atomically, creating the cursor row when absent.
     *
     * <p>Implemented as a single PostgreSQL upsert ({@code INSERT ... ON CONFLICT DO UPDATE}): a
     * fresh {@code (user_id, device_id)} pair inserts a new cursor row, while an existing row is
     * advanced monotonically via {@code GREATEST} — a smaller watermark never rewinds the stored
     * value, and concurrent pulls converge on the maximum. {@code updated_at} is set explicitly
     * because native SQL bypasses the {@code @UpdateTimestamp} interceptor.
     *
     * <p><strong>Note:</strong> Must be called within a {@code @Transactional} context.
     *
     * @param userId the owning user id
     * @param deviceId the client device id
     * @param watermark the new watermark; the stored value only ever moves forward
     * @return number of rows affected ({@code 1} when the cursor row was inserted or touched)
     */
    @Modifying
    @Query(
            value =
                    """
                INSERT INTO sync_cursors (user_id, device_id, last_pulled_change_id, updated_at)
                VALUES (:userId, :deviceId, :watermark, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id, device_id)
                DO UPDATE SET
                    last_pulled_change_id = GREATEST(sync_cursors.last_pulled_change_id, EXCLUDED.last_pulled_change_id),
                    updated_at = CURRENT_TIMESTAMP
                """,
            nativeQuery = true)
    int advancePullWatermark(
            @Param("userId") UUID userId,
            @Param("deviceId") UUID deviceId,
            @Param("watermark") long watermark);
}
