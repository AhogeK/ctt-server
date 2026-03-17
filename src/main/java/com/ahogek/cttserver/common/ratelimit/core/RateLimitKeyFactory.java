package com.ahogek.cttserver.common.ratelimit.core;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.common.context.RequestContext;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;

import org.springframework.stereotype.Component;

/**
 * Factory for generating rate limit Redis keys based on dimension type.
 *
 * <p>Encapsulates the key generation logic for different rate limiting strategies (IP, USER, EMAIL,
 * API). Time complexity: O(1) for key assembly.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
@Component
public class RateLimitKeyFactory {

    private static final String KEY_PREFIX = "rate_limit";

    private final CurrentUserProvider currentUserProvider;

    public RateLimitKeyFactory(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Generates the Redis key for rate limiting based on the dimension type.
     *
     * <p>Key format: rate_limit:{type}:{apiPath}:{identifier}
     *
     * @param type the rate limit dimension type
     * @param apiPath the API path identifier (ClassName.methodName)
     * @param spElValue the value extracted from SpEL expression (used for EMAIL type)
     * @return the Redis key for rate limiting
     * @throws IllegalArgumentException if EMAIL type is used without valid SpEL value
     * @throws IllegalStateException if request context or user context is not available
     */
    public String generateKey(RateLimitType type, String apiPath, String spElValue) {
        return switch (type) {
            case IP ->
                    String.format(
                            "%s:ip:%s:%s",
                            KEY_PREFIX, apiPath, RequestContext.currentRequired().clientIp());
            case USER ->
                    String.format(
                            "%s:user:%s:%s",
                            KEY_PREFIX, apiPath, currentUserProvider.getCurrentUserRequired().id());
            case EMAIL -> {
                if (spElValue == null || spElValue.isBlank()) {
                    throw new IllegalArgumentException(
                            "SpEL expression evaluation failed for EMAIL rate limit");
                }
                yield String.format("%s:email:%s:%s", KEY_PREFIX, apiPath, spElValue);
            }
            case API -> String.format("%s:api:%s", KEY_PREFIX, apiPath);
        };
    }
}
