package com.ahogek.cttserver.sync.entity;

import com.ahogek.cttserver.sync.enums.ChangeOp;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Change log entry for incremental synchronization.
 *
 * <p>Maps to the {@code session_changes} table. Every accepted write (upsert or soft-delete)
 * appends one row so that devices can pull only what changed since their last watermark ({@code
 * change_id}). The {@code change_id} is a database sequence (BIGSERIAL) and therefore monotonically
 * increasing per database, which makes it a safe pull cursor.
 *
 * <p>References to the owning user, originating device and affected session are deliberately kept
 * as scalar UUIDs rather than JPA associations so the sync module stays dependency-light and the
 * change log can be written without loading the referenced entities.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-25
 */
@Entity
@Table(name = "session_changes")
public class SessionChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long changeId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "op", nullable = false, length = 10)
    private ChangeOp op;

    @Column(name = "server_version", nullable = false)
    private long serverVersion;

    @CreationTimestamp
    @Column(name = "happened_at", nullable = false, updatable = false)
    private Instant happenedAt;

    public SessionChange() {}

    public Long getChangeId() {
        return changeId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(UUID deviceId) {
        this.deviceId = deviceId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public ChangeOp getOp() {
        return op;
    }

    public void setOp(ChangeOp op) {
        this.op = op;
    }

    public long getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(long serverVersion) {
        this.serverVersion = serverVersion;
    }

    public Instant getHappenedAt() {
        return happenedAt;
    }
}
