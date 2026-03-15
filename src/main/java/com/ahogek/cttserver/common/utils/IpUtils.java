package com.ahogek.cttserver.common.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Utility for extracting real client IP address, handling proxy headers.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-15
 */
public final class IpUtils {

    private static final String[] PROXY_HEADERS = {
        "X-Forwarded-For",
        "X-Real-IP",
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP",
        "HTTP_CLIENT_IP",
        "HTTP_X_FORWARDED_FOR"
    };

    private static final String UNKNOWN = "unknown";
    private static final String IPV6_LOCALHOST = "::1";

    private IpUtils() {}

    public static String getRealIp(HttpServletRequest request) {
        for (String header : PROXY_HEADERS) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip)) {
                if (ip.contains(",")) {
                    return ip.split(",")[0].trim();
                }
                return normalizeIp(ip);
            }
        }
        return normalizeIp(request.getRemoteAddr());
    }

    private static String normalizeIp(String ip) {
        if (IPV6_LOCALHOST.equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }
}
