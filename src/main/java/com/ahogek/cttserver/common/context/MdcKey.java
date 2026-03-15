package com.ahogek.cttserver.common.context;

/**
 * MDC key constants for logging.
 *
 * <p>These keys map to logback-spring.xml %X{key} and JSON log fields in production.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-15
 */
public final class MdcKey {

    private MdcKey() {}

    public static final String TRACE_ID = "traceId";
    public static final String CLIENT_IP = "clientIp";
    public static final String DEVICE_ID = "deviceId";
    public static final String HTTP_METHOD = "httpMethod";
    public static final String REQUEST_URI = "requestUri";

    public static final String USER_ID = "userId";
}
