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

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AOP aspect that enforces {@link RequiresApiKeyScope} authorization.
 *
 * <p>Intercepts controller methods annotated with {@link RequiresApiKeyScope} and validates that
 * the current authentication principal holds at least one of the required scopes. For
 * JWT-authenticated users (principal is {@link com.ahogek.cttserver.auth.model.CurrentUser}), the
 * check is bypassed entirely.
 *
 * <p>When an API key lacks every required scope, the aspect throws {@link ForbiddenException} with
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
     * <p>A key is authorized when it holds at least one of the required scopes, or holds the {@link
     * ApiKeyScope#ADMIN} scope which overrides every requirement.
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
            ApiKeyScope[] requiredScopes = requiresApiKeyScope.value();
            Set<ApiKeyScope> grantedScopes = apiKeyPrincipal.scopes();

            boolean holdsAnyRequired =
                    Arrays.stream(requiredScopes).anyMatch(grantedScopes::contains);
            if (!holdsAnyRequired && !grantedScopes.contains(ApiKeyScope.ADMIN)) {
                String required =
                        Arrays.stream(requiredScopes)
                                .map(Enum::name)
                                .collect(Collectors.joining(", "));
                // Method name is only resolved on the denial path; keep it out of the log call so
                // it is not evaluated when the WARN level is disabled.
                String methodName = extractMethodName(joinPoint);
                log.atWarn()
                        .addKeyValue("keyId", apiKeyPrincipal.keyId())
                        .addKeyValue("method", methodName)
                        .addKeyValue("requiredScopes", required)
                        .log("API key denied access - missing required scope(s)");

                auditLogService.logFailure(
                        apiKeyPrincipal.userId(),
                        AuditAction.API_KEY_SCOPE_DENIED,
                        ResourceType.API_KEY,
                        apiKeyPrincipal.keyId().toString(),
                        required);

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
