package com.ahogek.cttserver.sync.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Coding session aggregate root representing a tracked coding activity.
 *
 * <p>Maps to the {@code coding_sessions} table. Sessions are the unit of synchronization between
 * the IDE plugin and the server: the plugin pushes local edits, the server resolves conflicts with
 * last-write-wins semantics using {@code client_version} (client-side counter) and {@code
 * server_version} (server-side watermark), and soft-deletes are propagated through the change log
 * instead of physical row removal so that all devices converge on the same state.
 *
 * <p>State transitions are encapsulated by {@link #softDelete(Instant)}, {@link #restore()} and
 * {@link #bumpServerVersion()} — the preferred mutation path for business logic. Raw setters exist
 * for JPA hydration and should not be used to drive lifecycle changes.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-25
 */
@Entity
@Table(
        name = "coding_sessions",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_coding_sessions_user_session_uuid",
                    columnNames = {"user_id", "session_uuid"})
        })
public class CodingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "session_uuid", nullable = false)
    private UUID sessionUuid;

    @Column(name = "project_name", nullable = false)
    private String projectName;

    @Column(name = "language", nullable = false, length = 50)
    private String language;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "client_modified_at", nullable = false)
    private Instant clientModifiedAt;

    @Column(name = "client_version", nullable = false)
    private int clientVersion;

    @Column(name = "server_version", nullable = false)
    private long serverVersion;

    @Column(name = "updated_by_device_id")
    private UUID updatedByDeviceId;

    @Column(name = "origin_device_id")
    private UUID originDeviceId;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CodingSession() {}

    // ==========================================
    // Lifecycle behaviors
    // ==========================================

    /**
     * Marks the session as soft-deleted.
     *
     * <p>Idempotent: soft-deleting an already-deleted session is a no-op and preserves the original
     * {@code deletedAt} timestamp so the change log stays consistent across devices.
     *
     * @param when the deletion instant; typically {@link Instant#now()}
     */
    public void softDelete(Instant when) {
        if (!isDeleted) {
            this.isDeleted = true;
            this.deletedAt = when;
        }
    }

    /**
     * Restores a soft-deleted session.
     *
     * <p>Clears both the flag and the deletion timestamp so the session becomes visible to sync
     * queries again. Safe to call on a live session (no-op state-wise).
     */
    public void restore() {
        this.isDeleted = false;
        this.deletedAt = null;
    }

    /**
     * Advances the server-side version watermark by one.
     *
     * <p>Called whenever the server accepts a write so that downstream devices can detect the
     * change via the {@code server_version} watermark and the session change log.
     */
    public void bumpServerVersion() {
        this.serverVersion++;
    }

    /**
     * Records the device that produced this server-side write.
     *
     * <p>Called on every accepted push (create, update and delete paths) so {@code
     * updated_by_device_id} always names the last writer. The origin device, by contrast, is
     * stamped once at creation and never rewritten.
     *
     * @param deviceId the pushing device
     */
    public void touchByDevice(UUID deviceId) {
        this.updatedByDeviceId = deviceId;
    }

    // ==========================================
    // Getters and Setters
    // ==========================================

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

    public UUID getSessionUuid() {
        return sessionUuid;
    }

    public void setSessionUuid(UUID sessionUuid) {
        this.sessionUuid = sessionUuid;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public Instant getClientModifiedAt() {
        return clientModifiedAt;
    }

    public void setClientModifiedAt(Instant clientModifiedAt) {
        this.clientModifiedAt = clientModifiedAt;
    }

    public int getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(int clientVersion) {
        this.clientVersion = clientVersion;
    }

    public long getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(long serverVersion) {
        this.serverVersion = serverVersion;
    }

    public UUID getOriginDeviceId() {
        return originDeviceId;
    }

    public void setOriginDeviceId(UUID originDeviceId) {
        this.originDeviceId = originDeviceId;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
