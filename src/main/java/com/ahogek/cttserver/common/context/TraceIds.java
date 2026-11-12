package com.ahogek.cttserver.common.context;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Resolves the trace identifier that belongs to the current work.
 *
 * <p>One identifier ties the log line, the {@code X-Trace-Id} response header and the exported
 * span. Anything that reports a trace id to a caller — an error body, an audit entry — must read it
 * from here instead of minting one, otherwise the body would name a trace that does not exist.
 *
 * <p>The request context is authoritative because it is bound before any handler runs. The MDC is
 * the fallback for code that executes on a thread where the scoped value is not bound, such as a
 * servlet filter that has not yet entered the scope. Only work with neither a context nor an MDC
 * receives a fresh identifier, and that identifier is a valid 32-character W3C trace id.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-12
 */
public final class TraceIds {

    private TraceIds() {}

    public static String current() {
        return RequestContext.current()
                .map(RequestInfo::traceId)
                .filter(traceId -> !traceId.isBlank())
                .orElseGet(TraceIds::fromMdc);
    }

    private static String fromMdc() {
        String traceId = MDC.get(MdcKey.TRACE_ID);
        return (traceId != null && !traceId.isBlank())
                ? traceId
                : UUID.randomUUID().toString().replace("-", "");
    }
}
