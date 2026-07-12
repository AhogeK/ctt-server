package com.ahogek.cttserver.auth.apikey.security;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;
import com.ahogek.cttserver.auth.apikey.model.ApiKeyPrincipal;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * AOP aspect that enforces {@link RequiresApiKeyScope} authorization.
 *
 * <p>Intercepts controller methods annotated with {@link RequiresApiKeyScope} and validates that
 * the current authentication principal has the required scope. For JWT-authenticated users
 * (principal is {@link com.ahogek.cttserver.auth.model.CurrentUser}), the check is bypassed
 * entirely.
 *
 * <p>When an API key lacks the required scope, the aspect throws {@link ForbiddenException} with
 * {@link ErrorCode#AUTH_020} and logs an {@link AuditAction#API_KEY_SCOPE_DENIED} audit event.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-12
 */
@Aspect
@Component
public class ApiKeyScopeAspect {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyScopeAspect.class);

    private final AuditLogService auditLogService;

    public ApiKeyScopeAspect(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * Intercepts methods annotated with {@link RequiresApiKeyScope} and validates scope.
     *
     * @param joinPoint the intercepted method invocation
     * @param requiresApiKeyScope the annotation instance (injected by Spring AOP)
     * @return the method result if authorization passes
     * @throws Throwable if authorization fails or the method throws
     */
    @Around("@annotation(requiresApiKeyScope)")
    public Object enforceScope(
            ProceedingJoinPoint joinPoint, RequiresApiKeyScope requiresApiKeyScope)
            throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return joinPoint.proceed();
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof ApiKeyPrincipal apiKeyPrincipal) {
            ApiKeyScope requiredScope = requiresApiKeyScope.value();
            Set<ApiKeyScope> grantedScopes = apiKeyPrincipal.scopes();

            if (!grantedScopes.contains(requiredScope)
                    && !grantedScopes.contains(ApiKeyScope.ADMIN)) {
                log.warn(
                        "API key {} denied access to {} - missing required scope {}",
                        apiKeyPrincipal.keyId(),
                        extractMethodName(joinPoint),
                        requiredScope);

                auditLogService.logFailure(
                        apiKeyPrincipal.userId(),
                        AuditAction.API_KEY_SCOPE_DENIED,
                        ResourceType.API_KEY,
                        apiKeyPrincipal.keyId().toString(),
                        requiredScope.name());

                throw new ForbiddenException(ErrorCode.AUTH_020);
            }
        }

        return joinPoint.proceed();
    }

    private String extractMethodName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }
}
