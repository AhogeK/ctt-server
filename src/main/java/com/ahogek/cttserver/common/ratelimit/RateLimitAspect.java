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

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

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

    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer nameDiscoverer =
            new DefaultParameterNameDiscoverer();

    public RateLimitAspect(
            RateLimitKeyFactory keyFactory,
            RedisRateLimiter redisRateLimiter,
            AuditLogService auditLog,
            CurrentUserProvider currentUserProvider) {
        this.keyFactory = keyFactory;
        this.redisRateLimiter = redisRateLimiter;
        this.auditLog = auditLog;
        this.currentUserProvider = currentUserProvider;
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
        String spElValue = resolveSpEl(joinPoint, signature, rateLimit.keyExpression());

        // Generate Redis key
        String cacheKey = keyFactory.generateKey(rateLimit.type(), apiPath, spElValue);

        // Check rate limit
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
     * Resolves SpEL expression to extract dynamic value from method arguments.
     *
     * @param joinPoint the proceeding join point
     * @param signature the method signature
     * @param expression the SpEL expression
     * @return the resolved value as string, or null if expression is empty
     */
    private String resolveSpEl(
            ProceedingJoinPoint joinPoint, MethodSignature signature, String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }

        String[] paramNames = nameDiscoverer.getParameterNames(signature.getMethod());
        Object[] args = joinPoint.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        Object value = parser.parseExpression(expression).getValue(context);
        return value != null ? value.toString() : null;
    }

    /**
     * Gets current user ID if authenticated.
     *
     * @return user ID or null if not authenticated
     */
    private java.util.UUID getCurrentUserId() {
        return currentUserProvider.getCurrentUser().map(CurrentUser::id).orElse(null);
    }
}
