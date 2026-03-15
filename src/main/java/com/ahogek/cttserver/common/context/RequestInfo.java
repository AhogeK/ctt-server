package com.ahogek.cttserver.common.context;

import org.jspecify.annotations.Nullable;

/**
 * Request context data carrier (immutable).
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-15
 */
public record RequestInfo(
        String traceId,
        String clientIp,
        String userAgent,
        String requestUri,
        String method,
        @Nullable String deviceId) {

    public boolean isFromDevice() {
        return deviceId != null && !deviceId.isBlank();
    }
}
