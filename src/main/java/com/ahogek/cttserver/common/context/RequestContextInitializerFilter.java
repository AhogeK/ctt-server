package com.ahogek.cttserver.common.context;

import com.ahogek.cttserver.common.utils.DesensitizeUtils;
import com.ahogek.cttserver.common.utils.IpUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
    private static final int MAX_CLIENT_HEADER_LENGTH = 128;
    private static final int MAX_TRACE_LENGTH = 32;
    private static final String HSTS_HEADER = "Strict-Transport-Security";
    private static final String HSTS_VALUE = "max-age=31536000; includeSubDomains";
    private final Set<String> trustedProxies;

    public RequestContextInitializerFilter(
            @Value("${ctt.security.trusted-proxy-addresses:}") String trustedProxyAddresses) {
        this.trustedProxies = IpUtils.parseTrustedProxies(trustedProxyAddresses);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, @NonNull FilterChain chain) {

        String traceId =
                Optional.ofNullable(request.getHeader(TRACE_HEADER))
                        .filter(s -> !s.isBlank())
                        .filter(s -> s.length() == MAX_TRACE_LENGTH)
                        .filter(s -> s.matches("^[0-9a-fA-F]{32}$"))
                        .map(String::toLowerCase)
                        .orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));

        ClientIdentity clientIdentity = extractClientIdentity(request);

        RequestInfo requestInfo =
                new RequestInfo(
                        traceId,
                        IpUtils.getRealIp(request, trustedProxies),
                        request.getHeader("User-Agent"),
                        request.getRequestURI(),
                        request.getMethod(),
                        clientIdentity);

        response.setHeader(TRACE_HEADER, traceId);
        if (IpUtils.isTrustedProxy(request, trustedProxies)
                && "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"))) {
            response.setHeader(HSTS_HEADER, HSTS_VALUE);
        }

        MDC.put(MdcKey.TRACE_ID, traceId);
        MDC.put(MdcKey.CLIENT_IP, DesensitizeUtils.maskIp(requestInfo.clientIp()));
        MDC.put(MdcKey.HTTP_METHOD, requestInfo.method());
        MDC.put(MdcKey.REQUEST_URI, requestInfo.requestUri());
        Optional.ofNullable(clientIdentity.deviceId())
                .ifPresent(id -> MDC.put(MdcKey.DEVICE_ID, id.toString()));
        if (clientIdentity.platform() != null) {
            MDC.put(MdcKey.PLATFORM, clientIdentity.platform());
        }

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
            MDC.remove(MdcKey.TRACE_ID);
            MDC.remove(MdcKey.CLIENT_IP);
            MDC.remove(MdcKey.HTTP_METHOD);
            MDC.remove(MdcKey.REQUEST_URI);
            MDC.remove(MdcKey.DEVICE_ID);
            MDC.remove(MdcKey.PLATFORM);
        }
    }

    /**
     * Extracts structured client identity from HTTP headers.
     *
     * <p>Parses client metadata headers into a strongly-typed ClientIdentity record. Invalid UUID
     * formats are gracefully ignored.
     *
     * @param request HTTP servlet request
     * @return ClientIdentity containing parsed header values
     */
    private ClientIdentity extractClientIdentity(HttpServletRequest request) {
        String deviceIdStr = request.getHeader(ClientHeaderConstants.HEADER_DEVICE_ID);
        UUID deviceId = null;
        try {
            if (deviceIdStr != null && !deviceIdStr.isBlank()) {
                deviceId = UUID.fromString(deviceIdStr);
            }
        } catch (IllegalArgumentException _) {
            // Defensively ignore invalid UUID format, degrade to null
        }

        return new ClientIdentity(
                deviceId,
                sanitizeHeader(request.getHeader(ClientHeaderConstants.HEADER_DEVICE_NAME)),
                sanitizeHeader(request.getHeader(ClientHeaderConstants.HEADER_PLATFORM)),
                sanitizeHeader(request.getHeader(ClientHeaderConstants.HEADER_IDE_NAME)),
                sanitizeHeader(request.getHeader(ClientHeaderConstants.HEADER_IDE_VERSION)),
                sanitizeHeader(request.getHeader(ClientHeaderConstants.HEADER_APP_VERSION)));
    }

    /**
     * Sanitizes header value by truncating and trimming.
     *
     * @param value raw header value
     * @return sanitized value or null if blank
     */
    private String sanitizeHeader(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > MAX_CLIENT_HEADER_LENGTH
                ? trimmed.substring(0, MAX_CLIENT_HEADER_LENGTH)
                : trimmed;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable e) throws T {
        throw (T) e;
    }
}
