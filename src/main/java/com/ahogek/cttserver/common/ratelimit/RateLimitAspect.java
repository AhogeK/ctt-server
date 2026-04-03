package com.ahogek.cttserver.common.ratelimit;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.TooManyRequestsException;
import com.ahogek.cttserver.common.ratelimit.core.RateLimitKeyFactory;
import com.ahogek.cttserver.common.ratelimit.core.RedisRateLimiter;
import com.ahogek.cttserver.common.util.SpelExpressionResolver;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * AOP aspect for enforcing rate limits declared via {@link RateLimit} annotations.
 *
 * <p>Intercepts methods annotated with {@link RateLimit}, extracts the appropriate key based on the
 * dimension type (IP, USER, EMAIL, API), and checks against Redis rate limiter. Throws
 * TooManyRequestsException if limit exceeded and logs security audit event.
 *
 * <p>Supports SpEL expressions for dynamic key extraction (e.g., extracting email from request
 * body).
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
@Aspect
@Component
public class RateLimitAspect {

    private final RateLimitKeyFactory keyFactory;
    private final RedisRateLimiter redisRateLimiter;
    private final AuditLogService auditLog;
    private final CurrentUserProvider currentUserProvider;
    private final SpelExpressionResolver spelResolver;

    public RateLimitAspect(
            RateLimitKeyFactory keyFactory,
            RedisRateLimiter redisRateLimiter,
            AuditLogService auditLog,
            CurrentUserProvider currentUserProvider,
            SpelExpressionResolver spelResolver) {
        this.keyFactory = keyFactory;
        this.redisRateLimiter = redisRateLimiter;
        this.auditLog = auditLog;
        this.currentUserProvider = currentUserProvider;
        this.spelResolver = spelResolver;
    }

    /**
     * Intercepts methods annotated with {@link RateLimit} and enforces rate limiting.
     *
     * @param joinPoint the proceeding join point
     * @param rateLimit the rate limit annotation
     * @return the method result if allowed
     * @throws Throwable if rate limit exceeded or method execution fails
     */
    @Around("@annotation(rateLimit)")
    public Object intercept(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String apiPath =
                signature.getDeclaringType().getSimpleName()
                        + "."
                        + signature.getMethod().getName();

        // Parse SpEL expression for dynamic key extraction
        String spElValue = spelResolver.resolve(joinPoint, signature, rateLimit.keyExpression());

        String cacheKey = keyFactory.generateKey(rateLimit.type(), apiPath, spElValue);

        if (!redisRateLimiter.isAllowed(cacheKey, rateLimit.limit(), rateLimit.windowSeconds())) {

            // Log security audit event
            auditLog.logFailure(
                    getCurrentUserId(),
                    AuditAction.RATE_LIMIT_EXCEEDED,
                    ResourceType.UNKNOWN,
                    cacheKey,
                    "Rate limit exceeded for " + rateLimit.type().name());

            throw new TooManyRequestsException(
                    ErrorCode.RATE_LIMIT_001, "Too many requests, please try again later.");
        }

        return joinPoint.proceed();
    }

    /**
     * Gets current user ID if authenticated.
     *
     * @return user ID or null if not authenticated
     */
    private UUID getCurrentUserId() {
        return currentUserProvider.getCurrentUser().map(CurrentUser::id).orElse(null);
    }
}
