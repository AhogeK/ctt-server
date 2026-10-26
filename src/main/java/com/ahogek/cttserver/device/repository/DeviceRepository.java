package com.ahogek.cttserver.device.repository;

import com.ahogek.cttserver.device.entity.Device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
     * Records that a device was active, without loading and saving the entity.
     *
     * <p>Written as a single statement on purpose: {@link Device} carries {@code @Version}, and a
     * read-modify-write from two syncs of the same installation (the device id is shared across the
     * plugins that share an install) would race into an optimistic-lock failure for a heartbeat
     * that carries no business meaning. A bulk update also keeps the update off the entity's
     * version, so a heartbeat can never invalidate a concurrent registration.
     *
     * <p>The {@code revokedAt is null} condition keeps a revoked device's clock frozen: revocation
     * is the one event that should stop it looking active.
     *
     * @param userId the owner, part of the predicate so a mismatched pair updates nothing
     * @param deviceId the device that made the request
     * @param seenAt the instant of the request
     * @return the number of rows updated (0 when the device is unknown, not owned, or revoked)
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
            """)
    int touchLastSeen(
            @Param("userId") UUID userId,
            @Param("deviceId") UUID deviceId,
            @Param("seenAt") Instant seenAt);
}
