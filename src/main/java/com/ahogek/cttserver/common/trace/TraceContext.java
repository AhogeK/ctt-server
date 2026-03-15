package com.ahogek.cttserver.common.trace;

import org.slf4j.MDC;

/**
 * Utility class for accessing trace context.
 *
 * <p>Provides a static method to retrieve the current trace ID from MDC, avoiding direct MDC access
 * in business code. Returns "untraced" if no trace ID is available.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-15
 */
public final class TraceContext {

    private TraceContext() {}

    public static String current() {
        String id = MDC.get(TraceIdFilter.MDC_KEY);
        return (id != null && !id.isBlank()) ? id : "untraced";
    }
}
