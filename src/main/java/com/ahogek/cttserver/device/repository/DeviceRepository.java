package com.ahogek.cttserver.device.repository;

import com.ahogek.cttserver.device.entity.Device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Device entity.
 *
 * <p>Provides user-scoped device queries for device management operations.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-28
 */
@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {

    /**
     * Finds all devices belonging to a specific user, ordered by last activity.
     *
     * @param userId the user ID
     * @return list of devices ordered by lastSeenAt descending
     */
    List<Device> findByUserIdOrderByLastSeenAtDesc(UUID userId);

    /**
     * Finds a device by ID and user ID (ownership check).
     *
     * @param id the device ID
     * @param userId the user ID
     * @return the device if found and owned by the user
     */
    Optional<Device> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Minimum age of {@code last_seen_at} before a heartbeat writes it again.
     *
     * <p>A poll is the plugin's heartbeat at 406-540 requests/day/device (measured 2026-11-05), so
     * an unconditional update rewrites the same row every few minutes for a value nothing reads at
     * that resolution: the device list answers "is this installation alive", where fifteen minutes
     * of lag is invisible.
     */
    Duration LAST_SEEN_WRITE_INTERVAL = Duration.ofMinutes(15);

    /**
     * Records that a device was active, without loading and saving the entity.
     *
     * <p>Written as a single statement on purpose: {@link Device} carries {@code @Version}, and a
     * read-modify-write from two syncs of the same installation (the device id is shared across the
     * plugins that share an install) would race into an optimistic-lock failure for a heartbeat
     * that carries no business meaning. A bulk update also keeps the update off the entity's
     * version, so a heartbeat can never invalidate a concurrent registration.
     *
     * <p>A heartbeat inside {@link #LAST_SEEN_WRITE_INTERVAL} matches nothing: the value it would
     * write is the one already stored, and the predicate is what keeps an idle installation from
     * producing a row version every few minutes.
     *
     * @param userId the owner, part of the predicate so a mismatched pair updates nothing
     * @param deviceId the device that made the request
     * @param seenAt the instant of the request
     * @return the number of rows updated (0 when the device is unknown, not owned, revoked, or
     *     already seen inside the window)
     */
    default int touchLastSeen(UUID userId, UUID deviceId, Instant seenAt) {
        return touchLastSeen(userId, deviceId, seenAt, seenAt.minus(LAST_SEEN_WRITE_INTERVAL));
    }

    /**
     * Records a heartbeat when the stored one is older than {@code staleBefore}.
     *
     * <p>The {@code revokedAt is null} condition keeps a revoked device's clock frozen: revocation
     * is the one event that should stop it looking active.
     *
     * @param userId the owner
     * @param deviceId the device that made the request
     * @param seenAt the instant of the request
     * @param staleBefore the cutoff above which the stored heartbeat is considered current
     * @return the number of rows updated
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update Device d
               set d.lastSeenAt = :seenAt,
                   d.updatedAt = :seenAt
             where d.id = :deviceId
               and d.userId = :userId
               and d.revokedAt is null
               and d.lastSeenAt < :staleBefore
            """)
    int touchLastSeen(
            @Param("userId") UUID userId,
            @Param("deviceId") UUID deviceId,
            @Param("seenAt") Instant seenAt,
            @Param("staleBefore") Instant staleBefore);
}
