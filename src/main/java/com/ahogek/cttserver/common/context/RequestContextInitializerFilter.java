package com.ahogek.cttserver.common.context;

import com.ahogek.cttserver.common.utils.IpUtils;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Global request context and tracing filter.
 *
 * <p>Extracts HTTP request metadata and binds to MDC (for logging) and ScopedValue (for business
 * logic).
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-15
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class RequestContextInitializerFilter extends OncePerRequestFilter {

    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String DEVICE_ID_HEADER = "X-Device-Id";
    public static final String MDC_TRACE_KEY = "traceId";
    private static final int MAX_HEADER_LENGTH = 128;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, @NonNull FilterChain chain) {

        String traceId =
                Optional.ofNullable(request.getHeader(TRACE_HEADER))
                        .filter(s -> !s.isBlank())
                        .filter(s -> s.length() <= MAX_HEADER_LENGTH)
                        .orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));

        String deviceId =
                Optional.ofNullable(request.getHeader(DEVICE_ID_HEADER))
                        .filter(s -> !s.isBlank())
                        .filter(s -> s.length() <= MAX_HEADER_LENGTH)
                        .orElse(null);

        RequestInfo requestInfo =
                new RequestInfo(
                        traceId,
                        IpUtils.getRealIp(request),
                        request.getHeader("User-Agent"),
                        request.getRequestURI(),
                        request.getMethod(),
                        deviceId);

        response.setHeader(TRACE_HEADER, traceId);

        MDC.put(MDC_TRACE_KEY, traceId);
        try {
            ScopedValue.where(RequestContext.CONTEXT, requestInfo)
                    .run(
                            () -> {
                                try {
                                    chain.doFilter(request, response);
                                } catch (IOException | ServletException e) {
                                    throw sneakyThrow(e);
                                }
                            });
        } finally {
            MDC.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable e) throws T {
        throw (T) e;
    }
}
