package com.ahogek.cttserver.common.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

class TraceContextTest {

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void current_withMdcValue_returnsMdcValue() {
        MDC.put(TraceIdFilter.MDC_KEY, "test-trace-id");
        assertEquals("test-trace-id", TraceContext.current());
    }

    @Test
    void current_withoutMdcValue_returnsUntraced() {
        assertEquals("untraced", TraceContext.current());
    }

    @Test
    void current_withBlankMdcValue_returnsUntraced() {
        MDC.put(TraceIdFilter.MDC_KEY, "   ");
        assertEquals("untraced", TraceContext.current());
    }
}
