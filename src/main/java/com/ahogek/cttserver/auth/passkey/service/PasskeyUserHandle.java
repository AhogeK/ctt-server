package com.ahogek.cttserver.auth.passkey.service;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.UUID;

import com.yubico.webauthn.data.ByteArray;

/**
 * Converts the account UUID to the 16-byte WebAuthn user handle and back.
 *
 * <p>The handle is stable and opaque to the browser; it is not a credential identifier and never
 * contains an email address.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-03
 */
final class PasskeyUserHandle {

    private PasskeyUserHandle() {}

    static ByteArray toByteArray(UUID userId) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(userId.getMostSignificantBits());
        buffer.putLong(userId.getLeastSignificantBits());
        return new ByteArray(buffer.array());
    }

    static Optional<UUID> toUuid(ByteArray handle) {
        if (handle == null || handle.size() != 16) {
            return Optional.empty();
        }
        ByteBuffer buffer = ByteBuffer.wrap(handle.getBytes());
        return Optional.of(new UUID(buffer.getLong(), buffer.getLong()));
    }
}
