package com.ahogek.cttserver.device.repository;

import com.ahogek.cttserver.device.entity.Device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
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
    java.util.Optional<Device> findByIdAndUserId(UUID id, UUID userId);
}
