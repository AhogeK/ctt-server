package com.ahogek.cttserver.common.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP request access logging filter.
 *
 * <p>Records the complete lifecycle of all HTTP requests (entry, exit, duration, status code). This
 * is the foundation for calculating SLA metrics (e.g., P99 latency) and troubleshooting external
 * network failures.
 *
 * <p><strong>Design Principles:</strong>
 *
 * <ul>
 *   <li>Non-invasive: Intercepts uniformly through an independent Filter
 *   <li>Precise timing: Records ΔT = T_end - T_start in nanoseconds
 *   <li>Security-first: Never logs full Request/Response Body (prevents memory overflow and
 *       sensitive data leakage), only metadata
 *   <li>Structured output: Uses SLF4J 2.x Fluent API with key-value pairs for machine parsing
 * </ul>
 *
 * <p><strong>Order Constraint:</strong> Must be ordered after {@link
 * RequestContextInitializerFilter} to ensure MDC context (traceId, clientIp) is available.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("ACCESS_LOG");

    /**
     * Threshold for slow request warning: ΔT_warn ≥ 500ms
     */
    private static final long SLOW_REQUEST_THRESHOLD_MS = 500L;

    // Structured logging field keys
    private static final String DURATION_MS_KEY = "duration_ms";
    private static final String DURATION_NS_KEY = "duration_ns";
    private static final String STATUS_KEY = "status";
    private static final String METHOD_KEY = "method";
    private static final String URI_KEY = "uri";
    private static final String QUERY_STRING_KEY = "query_string";
    private static final String CONTENT_LENGTH_KEY = "content_length";
    private static final String THRESHOLD_MS_KEY = "threshold_ms";
    private static final String ACTUAL_MS_KEY = "actual_ms";

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        FilterChain chain)
        throws ServletException, IOException {

        long startTime = System.nanoTime();

        try {
            chain.doFilter(request, response);
        } finally {
            long durationNanos = System.nanoTime() - startTime;
            long durationMillis = durationNanos / 1_000_000L;
            int status = response.getStatus();

            // Build structured log entry using Fluent API
            var logBuilder =
                ACCESS_LOG
                    .atInfo()
                    .addKeyValue(DURATION_MS_KEY, durationMillis)
                    .addKeyValue(DURATION_NS_KEY, durationNanos)
                    .addKeyValue(STATUS_KEY, status)
                    .addKeyValue(METHOD_KEY, request.getMethod())
                    .addKeyValue(URI_KEY, request.getRequestURI())
                    .addKeyValue(QUERY_STRING_KEY, request.getQueryString())
                    .addKeyValue(CONTENT_LENGTH_KEY, request.getContentLengthLong());

            // Human-readable message for console output
            logBuilder.log(
                "{} {} {} {}ms",
                request.getMethod(),
                request.getRequestURI(),
                status,
                durationMillis);

            // Slow request warning logic - use ACCESS_LOG with WARN level
            if (durationMillis >= SLOW_REQUEST_THRESHOLD_MS) {
                ACCESS_LOG
                    .atWarn()
                    .addKeyValue(THRESHOLD_MS_KEY, SLOW_REQUEST_THRESHOLD_MS)
                    .addKeyValue(ACTUAL_MS_KEY, durationMillis)
                    .addKeyValue(METHOD_KEY, request.getMethod())
                    .addKeyValue(URI_KEY, request.getRequestURI())
                    .log(
                        "Slow request detected: {} {} took {}ms (threshold: {}ms)",
                        request.getMethod(),
                        request.getRequestURI(),
                        durationMillis,
                        SLOW_REQUEST_THRESHOLD_MS);
            }

            // Error response logging (4xx/5xx)
            if (status >= 400) {
                ACCESS_LOG
                    .atWarn()
                    .addKeyValue(STATUS_KEY, status)
                    .addKeyValue(METHOD_KEY, request.getMethod())
                    .addKeyValue(URI_KEY, request.getRequestURI())
                    .addKeyValue(DURATION_MS_KEY, durationMillis)
                    .log(
                        "HTTP error response: {} {} returned {}",
                        request.getMethod(),
                        request.getRequestURI(),
                        status);
            }
        }
    }
}
