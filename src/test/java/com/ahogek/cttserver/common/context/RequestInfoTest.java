package com.ahogek.cttserver.common.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RequestInfo}.
 *
 * @author AhogeK
 * @since 2026-03-15
 */
@DisplayName("RequestInfo Tests")
class RequestInfoTest {

    @Test
    @DisplayName("Should return client identity when present")
    void shouldReturnClientIdentity() {
        UUID deviceId = UUID.randomUUID();
        ClientIdentity client =
                new ClientIdentity(
                        deviceId, "MacBook", "macOS", "IntelliJ IDEA", "2024.1", "1.0.0");
        RequestInfo info =
                new RequestInfo(
                        "trace-123", "127.0.0.1", "Mozilla/5.0", "/api/test", "GET", client);

        ClientIdentity result = info.client();

        assertNotNull(result);
        assertEquals(deviceId, result.deviceId());
        assertEquals("macOS", result.platform());
    }

    @Test
    @DisplayName("Should return empty identity when client identity is null")
    void shouldReturnEmptyIdentityWhenNull() {
        RequestInfo info =
                new RequestInfo("trace-123", "127.0.0.1", "Mozilla/5.0", "/api/test", "GET", null);

        ClientIdentity result = info.client();

        assertNotNull(result);
        assertNull(result.deviceId());
    }

    @Test
    @DisplayName("Should create full RequestInfo with all fields")
    void shouldCreateFullRequestInfo() {
        UUID deviceId = UUID.randomUUID();
        ClientIdentity client =
                new ClientIdentity(
                        deviceId, "MacBook", "macOS", "IntelliJ IDEA", "2024.1", "1.0.0");
        RequestInfo info =
                new RequestInfo(
                        "trace-123",
                        "192.168.1.1",
                        "Mozilla/5.0",
                        "/api/v1/sessions",
                        "POST",
                        client);

        assertEquals("trace-123", info.traceId());
        assertEquals("192.168.1.1", info.clientIp());
        assertEquals("Mozilla/5.0", info.userAgent());
        assertEquals("/api/v1/sessions", info.requestUri());
        assertEquals("POST", info.method());
        assertTrue(info.client().isPluginClient());
    }

    @Test
    @DisplayName("Should handle null client identity gracefully")
    void shouldHandleNullClientIdentity() {
        RequestInfo info =
                new RequestInfo("trace-123", "127.0.0.1", "Mozilla/5.0", "/api/test", "GET", null);

        assertNotNull(info.client());
        assertNull(info.client().deviceId());
    }
}
