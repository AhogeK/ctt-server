package com.ahogek.cttserver.common.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RequestContextTest {

    @AfterEach
    void tearDown() {
        RequestContext.current().ifPresent(_ -> {});
    }

    @Test
    void current_whenNotBound_returnsEmpty() {
        assertTrue(RequestContext.current().isEmpty());
    }

    @Test
    void current_whenBound_returnsValue() {
        RequestInfo info =
                new RequestInfo("trace-123", "127.0.0.1", "TestAgent", "/api/test", "GET", null);

        ScopedValue.where(RequestContext.CONTEXT, info)
                .run(
                        () -> {
                            assertTrue(RequestContext.current().isPresent());
                            assertEquals("trace-123", RequestContext.current().get().traceId());
                        });
    }

    @Test
    void currentRequired_whenNotBound_throwsException() {
        assertThrows(IllegalStateException.class, RequestContext::currentRequired);
    }

    @Test
    void currentRequired_whenBound_returnsValue() {
        RequestInfo info =
                new RequestInfo(
                        "trace-456", "192.168.1.1", "TestAgent", "/api/test", "POST", "device-789");

        ScopedValue.where(RequestContext.CONTEXT, info)
                .run(
                        () -> {
                            assertEquals("trace-456", RequestContext.currentRequired().traceId());
                            assertEquals(
                                    "192.168.1.1", RequestContext.currentRequired().clientIp());
                        });
    }

    @Test
    void currentRequired_whenTraceIdBlank_throwsException() {
        RequestInfo info = new RequestInfo("", "127.0.0.1", "TestAgent", "/api/test", "GET", null);

        ScopedValue.where(RequestContext.CONTEXT, info)
                .run(
                        () ->
                                assertThrows(
                                        IllegalStateException.class,
                                        RequestContext::currentRequired));
    }
}
