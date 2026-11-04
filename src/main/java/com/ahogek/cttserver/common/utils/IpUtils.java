package com.ahogek.cttserver.common.utils;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility for extracting the client IP without trusting caller-controlled forwarding headers by
 * default.
 *
 * <p>Forwarded headers are considered only when the direct socket peer is explicitly configured as
 * a trusted proxy. This prevents a client from rotating {@code X-Forwarded-For} to bypass IP rate
 * limits or to forge audit attribution.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-15
 */
public final class IpUtils {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String UNKNOWN = "unknown";
    private static final String IPV6_LOCALHOST = "::1";
    private static final String IPV6_EXPANDED_LOCALHOST = "0:0:0:0:0:0:0:1";
    private static final int MAX_IP_LENGTH = 45;

    private IpUtils() {}

    /**
     * Parses the configured trusted-proxy list into normalized addresses.
     *
     * @param trustedProxyAddresses comma-separated proxy addresses
     * @return immutable normalized proxy set
     */
    public static Set<String> parseTrustedProxies(String trustedProxyAddresses) {
        if (trustedProxyAddresses == null || trustedProxyAddresses.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(trustedProxyAddresses.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(IpUtils::normalizeIp)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns whether the direct socket peer is a configured trusted proxy.
     *
     * @param request HTTP servlet request
     * @param trustedProxies normalized trusted proxy addresses
     * @return true when forwarded headers may be considered
     */
    public static boolean isTrustedProxy(HttpServletRequest request, Set<String> trustedProxies) {
        return trustedProxies != null
                && !trustedProxies.isEmpty()
                && trustedProxies.contains(normalizeIp(request.getRemoteAddr()));
    }

    /**
     * Returns whether the request reached the application over TLS, including TLS terminated by a
     * configured trusted proxy.
     *
     * @param request HTTP servlet request
     * @param trustedProxies normalized trusted proxy addresses
     * @return true for direct TLS or trusted forwarded HTTPS
     */
    public static boolean isSecureRequest(HttpServletRequest request, Set<String> trustedProxies) {
        return request.isSecure()
                || (isTrustedProxy(request, trustedProxies)
                        && "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")));
    }

    /**
     * Returns the direct socket peer. Forwarded headers are deliberately ignored.
     *
     * @param request HTTP servlet request
     * @return normalized remote address
     */
    public static String getRealIp(HttpServletRequest request) {
        return normalizeIp(request.getRemoteAddr());
    }

    /**
     * Returns the client IP only when the direct peer is a configured trusted proxy.
     *
     * <p>The forwarded chain is read right-to-left. A client-controlled value on the left cannot
     * win when the trusted proxy appended the real peer on the right.
     *
     * @param request HTTP servlet request
     * @param trustedProxies exact proxy addresses allowed to supply forwarded headers
     * @return trusted client IP, or the direct socket peer
     */
    public static String getRealIp(HttpServletRequest request, Set<String> trustedProxies) {
        String remote = normalizeIp(request.getRemoteAddr());
        if (trustedProxies == null
                || trustedProxies.isEmpty()
                || !trustedProxies.contains(remote)) {
            return remote;
        }

        String forwarded = request.getHeader(X_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            for (int i = hops.length - 1; i >= 0; i--) {
                String hop = sanitize(hops[i]);
                if (hop != null && !trustedProxies.contains(hop)) {
                    return hop;
                }
            }
        }

        String realIp = sanitize(request.getHeader(X_REAL_IP));
        return realIp != null ? realIp : remote;
    }

    private static String sanitize(String ip) {
        if (ip == null) {
            return null;
        }
        String value = ip.trim();
        if (value.isEmpty()
                || value.length() > MAX_IP_LENGTH
                || UNKNOWN.equalsIgnoreCase(value)
                || !value.matches("^[0-9a-fA-F:.]+$")) {
            return null;
        }
        return normalizeIp(value);
    }

    private static String normalizeIp(String ip) {
        if (ip == null) {
            return "0.0.0.0";
        }
        if (IPV6_LOCALHOST.equals(ip) || IPV6_EXPANDED_LOCALHOST.equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }
}
