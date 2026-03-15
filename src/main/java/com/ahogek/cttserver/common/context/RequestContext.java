package com.ahogek.cttserver.common.context;

import java.util.Optional;

/**
 * Modern Java request context holder based on ScopedValue.
 *
 * <p>Provides safe, low-overhead context propagation in virtual thread environments, replacing
 * traditional ThreadLocal.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-15
 */
public final class RequestContext {

    public static final ScopedValue<RequestInfo> CONTEXT = ScopedValue.newInstance();

    private RequestContext() {}

    public static Optional<RequestInfo> current() {
        return CONTEXT.isBound() ? Optional.of(CONTEXT.get()) : Optional.empty();
    }

    public static RequestInfo currentRequired() {
        if (!CONTEXT.isBound()) {
            throw new IllegalStateException("Not executing within a request context");
        }
        RequestInfo info = CONTEXT.get();
        if (info.traceId() == null || info.traceId().isBlank()) {
            throw new IllegalStateException("Trace ID is missing from request context");
        }
        return info;
    }
}
