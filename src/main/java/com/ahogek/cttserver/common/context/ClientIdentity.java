package com.ahogek.cttserver.common.context;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Unified client identity carrier.
 *
 * <p>Encapsulates client environment metadata from HTTP headers, providing a strongly-typed,
 * immutable domain model that abstracts multi-platform differences (Web, IDE plugin, OpenAPI
 * scripts).
 *
 * <p>Used as the foundation for device registration, refresh token binding, and API key validation.
 *
 * @param deviceId Unique device identifier (UUID), null for web anonymous or legacy clients
 * @param deviceName Human-readable device name (e.g., "Alice's MacBook Pro")
 * @param platform Operating system platform (e.g., "macOS", "Windows", "Linux", "Web")
 * @param ideName Host IDE name for plugin clients (e.g., "IntelliJ IDEA", "VSCode")
 * @param ideVersion Host IDE version
 * @param appVersion Client application or plugin version
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
public record ClientIdentity(
        @Nullable UUID deviceId,
        @Nullable String deviceName,
        @Nullable String platform,
        @Nullable String ideName,
        @Nullable String ideVersion,
        @Nullable String appVersion) {

    /**
     * Determines if the request originates from a strictly controlled IDE plugin client.
     *
     * @return true if IDE name is present and non-blank
     */
    public boolean isPluginClient() {
        return ideName != null && !ideName.isBlank();
    }

    /**
     * Returns an empty identity with all fields null.
     *
     * @return empty ClientIdentity instance
     */
    public static ClientIdentity empty() {
        return new ClientIdentity(null, null, null, null, null, null);
    }
}
