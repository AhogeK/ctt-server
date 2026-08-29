package com.ahogek.cttserver.device.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Device entity representing a registered client device.
 *
 * <p>Tracks device metadata for multi-device sync and session management. Devices are registered by
 * plugins via the device registration endpoint or during login from a new device ID.
 *
 * <p>The {@code id} is the client-supplied device identifier (not database-generated), so
 * {@code @GeneratedValue} is intentionally absent. {@code @Version} distinguishes new entities
 * (null) from existing ones (0+) so Spring Data persists new devices instead of merging.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-28
 */
@Entity
@Table(name = "devices")
public class Device {

    @Id private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_name", length = 255)
    private String deviceName;

    @Column(length = 50)
    private String platform;

    @Column(name = "ide_name", length = 100)
    private String ideName;

    @Column(name = "ide_version", length = 50)
    private String ideVersion;

    @Column(name = "app_version", length = 50)
    private String appVersion;

    @Column(name = "last_ip", length = 45)
    private String lastIp;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Optimistic locking version.
     *
     * <p>Left {@code null} for newly constructed entities so Spring Data treats them as new and
     * persists them; Hibernate assigns version 0 on insert and increments it on each update.
     */
    @Version
    @Column(name = "version")
    private Long version;

    public Device() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getIdeName() {
        return ideName;
    }

    public void setIdeName(String ideName) {
        this.ideName = ideName;
    }

    public String getIdeVersion() {
        return ideVersion;
    }

    public void setIdeVersion(String ideVersion) {
        this.ideVersion = ideVersion;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getLastIp() {
        return lastIp;
    }

    public void setLastIp(String lastIp) {
        this.lastIp = lastIp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
