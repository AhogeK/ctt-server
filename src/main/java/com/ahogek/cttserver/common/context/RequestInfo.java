package com.ahogek.cttserver.common.context;

/**
 * Request context data carrier (immutable).
 *
 * <p>Contains HTTP request metadata and client identity information extracted at the ingress layer.
 *
 * @param traceId Request tracing identifier
 * @param clientIp Client IP address (real IP behind proxies)
 * @param userAgent Raw User-Agent header string
 * @param requestUri Request URI path
 * @param method HTTP method
 * @param clientIdentity Structured client identity parsed from headers
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-15
 */
public record RequestInfo(
        String traceId,
        String clientIp,
        String userAgent,
        String requestUri,
        String method,
        ClientIdentity clientIdentity) {

    /**
     * Returns the client identity, never null.
     *
     * <p>If no client headers were provided, returns an empty ClientIdentity.
     *
     * @return ClientIdentity instance (never null)
     */
    public ClientIdentity client() {
        return clientIdentity != null ? clientIdentity : ClientIdentity.empty();
    }
}
