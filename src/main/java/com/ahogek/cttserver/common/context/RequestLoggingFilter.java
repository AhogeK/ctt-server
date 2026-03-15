package com.ahogek.cttserver.common.context;

import com.ahogek.cttserver.common.utils.DesensitizeUtils;
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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP request access logging filter with defensive header masking.
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
 *   <li>Defensive masking: All request headers are automatically desensitized using {@link
 *       DesensitizeUtils} when recorded (DEBUG level only)
 * </ul>
 *
 * <p><strong>Order Constraint:</strong> Must be ordered after {@link
 * RequestContextInitializerFilter} to ensure MDC context (traceId, clientIp) is available.
 *
 * <p><strong>Header Logging:</strong> Request headers are only logged at DEBUG level or higher
 * verbosity. All sensitive headers (Authorization, Cookie, X-API-Key, etc.) are automatically
 * masked using {@link DesensitizeUtils#maskHeader(String, String)} to prevent PII/token leakage.
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
    private static final String HEADERS_KEY = "headers";

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

            // Defensive integration: Only record headers at DEBUG level to avoid I/O overhead
            // All headers are automatically masked using DesensitizeUtils for security
            if (ACCESS_LOG.isDebugEnabled()) {
                logBuilder = logBuilder.addKeyValue(HEADERS_KEY, extractAndMaskHeaders(request));
            }

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

    /**
     * Safely extracts and masks all request headers.
     *
     * <p>Time complexity: O(H) where H is the number of headers.
     *
     * <p>All header values are passed through {@link DesensitizeUtils#maskHeader(String, String)}
     * to prevent sensitive data leakage (Authorization tokens, Cookies, API keys, etc.).
     *
     * @param request the HTTP request
     * @return map of header names to masked values
     */
    private Map<String, String> extractAndMaskHeaders(HttpServletRequest request) {
        Map<String, String> safeHeaders = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();

        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                String value = request.getHeader(name);
                // Force all headers through DesensitizeUtils to prevent accidental leakage
                safeHeaders.put(name, DesensitizeUtils.maskHeader(name, value));
            }
        }
        return safeHeaders;
    }
}
