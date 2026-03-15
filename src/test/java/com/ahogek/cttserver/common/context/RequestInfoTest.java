package com.ahogek.cttserver.common.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestInfoTest {

    @Test
    void isFromDevice_withValidDeviceId_returnsTrue() {
        RequestInfo info =
                new RequestInfo(
                        "trace-123", "127.0.0.1", "Mozilla/5.0", "/api/test", "GET", "device-456");
        assertTrue(info.isFromDevice());
    }

    @Test
    void isFromDevice_withNullDeviceId_returnsFalse() {
        RequestInfo info =
                new RequestInfo("trace-123", "127.0.0.1", "Mozilla/5.0", "/api/test", "GET", null);
        assertFalse(info.isFromDevice());
    }

    @Test
    void isFromDevice_withBlankDeviceId_returnsFalse() {
        RequestInfo info =
                new RequestInfo("trace-123", "127.0.0.1", "Mozilla/5.0", "/api/test", "GET", "   ");
        assertFalse(info.isFromDevice());
    }
}
