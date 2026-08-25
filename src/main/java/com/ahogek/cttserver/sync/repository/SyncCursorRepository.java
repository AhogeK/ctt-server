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
     * Advances a device's pull watermark in a single atomic statement.
     *
     * <p>The {@code lastPulledChangeId < :watermark} predicate is the concurrency-safe monotonic
     * guard: concurrent pulls can never rewind the watermark, and the update is a no-op when the
     * stored watermark is already at or beyond the supplied value. {@code updated_at} is set
     * explicitly because bulk JPQL updates bypass the {@code @UpdateTimestamp} interceptor.
     *
     * <p><strong>Note:</strong> Must be called within a {@code @Transactional} context.
     *
     * @param userId the owning user id
     * @param deviceId the client device id
     * @param watermark the new watermark; only applied when strictly greater than the stored value
     * @return number of rows updated ({@code 1} when advanced, {@code 0} when already at or beyond)
     */
    @Modifying
    @Query(
            """
        UPDATE SyncCursor c
        SET c.lastPulledChangeId = :watermark, c.updatedAt = CURRENT_TIMESTAMP
        WHERE c.userId = :userId AND c.deviceId = :deviceId AND c.lastPulledChangeId < :watermark
        """)
    int advancePullWatermark(
            @Param("userId") UUID userId,
            @Param("deviceId") UUID deviceId,
            @Param("watermark") long watermark);
}
