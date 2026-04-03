package com.ahogek.cttserver.common.idempotent;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.idempotent.core.IdempotentLocker;
import com.ahogek.cttserver.common.util.SpelExpressionResolver;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Aspect for enforcing method-level idempotency via Redis distributed locks.
 *
 * <p>Intercepts methods annotated with {@link Idempotent}. Uses SETNX command for lightweight
 * locking. Higher precedence (lower order) ensures it executes before business interceptors.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-17
 */
@Aspect
@Component
@Order(10)
public class IdempotentAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);

    private final IdempotentLocker locker;
    private final CurrentUserProvider currentUserProvider;
    private final SpelExpressionResolver spelResolver;

    public IdempotentAspect(
            IdempotentLocker locker,
            CurrentUserProvider currentUserProvider,
            SpelExpressionResolver spelResolver) {
        this.locker = locker;
        this.currentUserProvider = currentUserProvider;
        this.spelResolver = spelResolver;
    }

    /**
     * Intercepts methods annotated with {@link Idempotent} and enforces idempotency via distributed
     * lock.
     *
     * @param joinPoint the proceeding join point
     * @param idempotent the idempotent annotation
     * @return the method result if allowed
     * @throws Throwable if duplicate request blocked or method execution fails
     */
    @Around("@annotation(idempotent)")
    public Object intercept(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        String prefix =
                StringUtils.hasText(idempotent.prefix())
                        ? idempotent.prefix()
                        : signature.getDeclaringType().getSimpleName()
                                + "."
                                + signature.getMethod().getName();

        // Parse dynamic key via SpEL
        String dynamicKey = spelResolver.resolve(joinPoint, signature, idempotent.keyExpression());

        // Include user dimension
        String userPart = "ANONYMOUS";
        if (idempotent.includeUserId()) {
            userPart =
                    currentUserProvider
                            .getCurrentUser()
                            .map(user -> user.id().toString())
                            .orElse("ANONYMOUS");
        }

        String lockKey =
                String.format(
                        "idempotent:%s:%s:%s",
                        prefix, userPart, dynamicKey != null ? dynamicKey : "NO_KEY");

        // Try to acquire lock
        if (!locker.tryLock(lockKey, idempotent.expireSeconds())) {
            log.atWarn().addKeyValue("lockKey", lockKey).log("Idempotent collision detected");
            throw new ConflictException(ErrorCode.COMMON_003, idempotent.message());
        }

        try {
            // Execute business logic
            return joinPoint.proceed();
        } catch (Throwable ex) {
            // Release lock on exception to allow immediate retry with corrected parameters
            locker.unlock(lockKey);
            throw ex;
        }
    }
}
