package com.ahogek.cttserver.common.context;

/**
 * Client identity HTTP header contract constants.
 *
 * <p>Defines the standardized header names for cross-platform client identification. Clients
 * (especially IDE plugins) must explicitly declare their identity using these headers instead of
 * relying on User-Agent parsing.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
public final class ClientHeaderConstants {

    private ClientHeaderConstants() {}

    /** Client device unique identifier (UUID), generated and persisted by client. */
    public static final String HEADER_DEVICE_ID = "X-Device-ID";

    /** Client device friendly name (e.g., "Alice's MacBook Pro"). */
    public static final String HEADER_DEVICE_NAME = "X-Device-Name";

    /** Operating system platform (e.g., "macOS", "Windows", "Linux", "Web"). */
    public static final String HEADER_PLATFORM = "X-Platform";

    /** Host IDE name (e.g., "IntelliJ IDEA", "VSCode") - provided by plugin clients only. */
    public static final String HEADER_IDE_NAME = "X-IDE-Name";

    /** Host IDE version. */
    public static final String HEADER_IDE_VERSION = "X-IDE-Version";

    /** Plugin or client application version. */
    public static final String HEADER_APP_VERSION = "X-App-Version";
}
