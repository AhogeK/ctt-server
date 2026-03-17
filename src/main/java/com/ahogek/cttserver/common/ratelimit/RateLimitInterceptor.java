package com.ahogek.cttserver.common.ratelimit;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.common.context.RequestContext;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.TooManyRequestsException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor that enforces rate limits declared via {@link RateLimit} annotations.
 *
 * <p>Extracts the appropriate identity context based on the requested {@link RateLimitType} (e.g.,
 * User ID from {@link CurrentUserProvider}, IP from {@link RequestContext}).
 *
 * <p>Note: This is a skeletal implementation. The actual token bucket / sliding window logic with
 * Redis (e.g., Redisson or custom Lua scripts) will be integrated later.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final CurrentUserProvider currentUserProvider;

    public RateLimitInterceptor(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            // Also check class-level annotation
            rateLimit = handlerMethod.getBeanType().getAnnotation(RateLimit.class);
        }

        if (rateLimit == null) {
            return true;
        }

        String limiterKey = buildLimiterKey(rateLimit, handlerMethod);

        // TODO: Integrate actual Redis-based rate limiting logic here
        // boolean allowed = redisRateLimiter.tryAcquire(limiterKey, rateLimit.capacity(),
        // rateLimit.period(), rateLimit.unit());
        boolean allowed = true; // Placeholder for skeleton

        if (!allowed) {
            throw new TooManyRequestsException(
                    ErrorCode.RATE_LIMIT_001, "Rate limit exceeded for " + rateLimit.type());
        }

        return true;
    }

    private String buildLimiterKey(RateLimit rateLimit, HandlerMethod handlerMethod) {
        String baseKey = rateLimit.key();
        if (baseKey.isEmpty()) {
            baseKey =
                    handlerMethod.getBeanType().getSimpleName()
                            + "."
                            + handlerMethod.getMethod().getName();
        }

        String identifier = extractIdentity(rateLimit.type());
        return "ratelimit:" + baseKey + ":" + identifier;
    }

    private String extractIdentity(RateLimitType type) {
        return switch (type) {
            case USER ->
                    currentUserProvider
                            .getCurrentUserRequired()
                            .id()
                            .toString(); // Will naturally block unauthenticated access
            case IP -> RequestContext.currentRequired().clientIp();
            case DEVICE ->
                    RequestContext.current()
                            .map(info -> info.deviceId() != null ? info.deviceId() : "unknown")
                            .orElse("unknown");
            case GLOBAL -> "global";
        };
    }
}
