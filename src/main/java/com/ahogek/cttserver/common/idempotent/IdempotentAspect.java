package com.ahogek.cttserver.common.idempotent;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Aspect for enforcing method-level idempotency via distributed locks.
 *
 * <p>Intercepts methods annotated with {@link Idempotent}.
 * Note: This is a skeletal implementation. Actual Redis distributed lock (e.g., Redisson)
 * integration is required for production.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
@Aspect
@Component
public class IdempotentAspect {

    private final CurrentUserProvider currentUserProvider;

    public IdempotentAspect(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String lockKey = buildLockKey(joinPoint, idempotent);

        // TODO: Integrate actual Redis distributed lock logic here
        // RLock lock = redissonClient.getLock(lockKey);
        // boolean isLocked = lock.tryLock(0, idempotent.expire(), idempotent.unit());
        boolean isLocked = true; // Placeholder for skeleton

        if (!isLocked) {
            throw new ConflictException(
                    ErrorCode.COMMON_003, "Duplicate request detected. Please try again later.");
        }

        try {
            return joinPoint.proceed();
        } finally {
            // Note: For true idempotency, we might NOT want to unlock immediately if we want
            // to block duplicates for the entire 'expire' duration even after the first finishes.
            // Or we might unlock if it failed, but keep it locked if it succeeded.
            // lock.unlock();
        }
    }

    private String buildLockKey(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        // Fallback key: user_id + method_name
        String userId =
                currentUserProvider
                        .getCurrentUser()
                        .map(user -> user.id().toString())
                        .orElse("anonymous");

        String methodName = joinPoint.getSignature().toShortString();

        // TODO: Implement SpEL parsing if idempotent.key() is not empty
        return "idempotent:" + userId + ":" + methodName;
    }
}
