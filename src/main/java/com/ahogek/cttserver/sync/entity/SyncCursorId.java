package com.ahogek.cttserver.sync.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link SyncCursor}.
 *
 * <p>Identifies a sync cursor by the owning user and the client device, mirroring the {@code
 * PRIMARY KEY (user_id, device_id)} of the {@code sync_cursors} table. Used via {@code @IdClass} on
 * {@link SyncCursor}; the field names must match the entity's {@code @Id} fields exactly.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-25
 */
public class SyncCursorId implements Serializable {

    private UUID userId;

    private UUID deviceId;

    public SyncCursorId() {}

    public SyncCursorId(UUID userId, UUID deviceId) {
        this.userId = userId;
        this.deviceId = deviceId;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SyncCursorId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(deviceId, that.deviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, deviceId);
    }
}
