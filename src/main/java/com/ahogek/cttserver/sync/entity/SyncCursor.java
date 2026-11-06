package com.ahogek.cttserver.sync.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-device synchronization watermark.
 *
 * <p>Maps to the {@code sync_cursors} table with the composite primary key {@code (user_id,
 * device_id)} via {@link SyncCursorId}. Tracks how far a device has pulled ({@code
 * last_pulled_change_id}), so the pull path can resume from the watermark instead of re-reading the
 * whole change log. Pushes leave no cursor of their own: their watermark is the change id the next
 * pull resumes from.
 *
 * <p>The watermark is advanced monotonically: {@link #advancePullWatermark(long)} never moves
 * backwards (preferred business path), and the repository-level bulk update guards the same
 * invariant under concurrency. The raw setter remains for JPA hydration only.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-25
 */
@Entity
@IdClass(SyncCursorId.class)
@Table(name = "sync_cursors")
public class SyncCursor {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "last_pulled_change_id", nullable = false)
    private long lastPulledChangeId;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SyncCursor() {}

    // ==========================================
    // Lifecycle behaviors
    // ==========================================

    /**
     * Advances the pull watermark to the supplied change id.
     *
     * <p>Monotonic guard: the watermark only ever moves forward, so a stale pull response can never
     * rewind a device and cause it to re-apply already-seen changes.
     *
     * @param newChangeId the highest change id observed by the pull
     */
    public void advancePullWatermark(long newChangeId) {
        this.lastPulledChangeId = Math.max(this.lastPulledChangeId, newChangeId);
    }

    // ==========================================
    // Getters and Setters
    // ==========================================

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

    public long getLastPulledChangeId() {
        return lastPulledChangeId;
    }

    public void setLastPulledChangeId(long lastPulledChangeId) {
        this.lastPulledChangeId = lastPulledChangeId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
