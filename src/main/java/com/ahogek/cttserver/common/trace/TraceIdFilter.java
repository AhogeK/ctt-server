package com.ahogek.cttserver.common.trace;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Trace ID filter for request tracking.
 *
 * <p>Generates or propagates a unique trace ID for each HTTP request to enable distributed tracing.
 * The trace ID is stored in MDC for logging and propagated via HTTP headers.
 *
 * <p>Behavior:
 *
 * <ul>
 *   <li>If incoming request has {@code X-Trace-Id} header, reuse it
 *   <li>Otherwise generate a new UUID (32-char hex string)
 *   <li>Store in MDC under key {@code traceId}
 *   <li>Add {@code X-Trace-Id} response header
 *   <li>Always clear MDC in finally block to prevent ThreadLocal leaks
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-15
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String traceId =
                Optional.ofNullable(request.getHeader(TRACE_HEADER))
                        .filter(s -> !s.isBlank())
                        .orElseGet(this::generateTraceId);

        MDC.put(MDC_KEY, traceId);
        response.setHeader(TRACE_HEADER, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
