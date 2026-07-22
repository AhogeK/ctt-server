package com.ahogek.cttserver.auth.apikey.security;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;
import com.ahogek.cttserver.auth.apikey.model.ApiKeyPrincipal;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.user.enums.UserStatus;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyScopeAspect Unit Tests")
class ApiKeyScopeAspectTest {

    @Mock private AuditLogService auditLogService;
    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private MethodSignature methodSignature;

    @InjectMocks private ApiKeyScopeAspect aspect;

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID KEY_ID = UUID.fromString("770e8400-e29b-41d4-a716-446655440002");

    private static final CurrentUser TEST_USER =
            new CurrentUser(
                    USER_ID,
                    "test@example.com",
                    UserStatus.ACTIVE,
                    java.util.Set.of(),
                    CurrentUser.AuthenticationType.API_KEY);

    private RequiresApiKeyScope readAnnotation;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setUpMethodStubbing() throws NoSuchMethodException {
        lenient().when(joinPoint.getSignature()).thenReturn(methodSignature);
        Method method = TestController.class.getMethod("readEndpoint");
        lenient().when(methodSignature.getMethod()).thenReturn(method);
        lenient().when(methodSignature.getDeclaringType()).thenReturn(TestController.class);
        lenient().when(methodSignature.getName()).thenReturn("readEndpoint");
        readAnnotation = method.getAnnotation(RequiresApiKeyScope.class);
    }

    private void setAuthentication(ApiKeyPrincipal principal) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        List<SimpleGrantedAuthority> authorities =
                principal.scopes().stream()
                        .map(ApiKeyScope::getAuthority)
                        .map(SimpleGrantedAuthority::new)
                        .toList();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
        SecurityContextHolder.setContext(context);
    }

    @Nested
    @DisplayName("API Key with required scope")
    class WithRequiredScope {

        @Test
        @DisplayName("shouldAllowAccess_whenApiKeyHasRequiredScope")
        void shouldAllowAccess_whenApiKeyHasRequiredScope() throws Throwable {
            // Given
            ApiKeyPrincipal principal =
                    new ApiKeyPrincipal(TEST_USER, KEY_ID, Set.of(ApiKeyScope.READ));
            setAuthentication(principal);
            setUpMethodStubbing();
            given(joinPoint.proceed()).willReturn("result");

            // When
            Object result = aspect.enforceScope(joinPoint, readAnnotation);

            // Then
            assertThat(result).isEqualTo("result");
            then(joinPoint).should().proceed();
            then(auditLogService).should(never()).logFailure(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("shouldAllowAccess_whenApiKeyHasAdminScope")
        void shouldAllowAccess_whenApiKeyHasAdminScope() throws Throwable {
            // Given
            ApiKeyPrincipal principal =
                    new ApiKeyPrincipal(TEST_USER, KEY_ID, Set.of(ApiKeyScope.ADMIN));
            setAuthentication(principal);
            setUpMethodStubbing();
            given(joinPoint.proceed()).willReturn("result");

            // When
            Object result = aspect.enforceScope(joinPoint, readAnnotation);

            // Then
            assertThat(result).isEqualTo("result");
            then(joinPoint).should().proceed();
            then(auditLogService).should(never()).logFailure(any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("API Key without required scope")
    class WithoutRequiredScope {

        @Test
        @DisplayName("shouldDenyAccess_whenApiKeyLacksRequiredScope")
        void shouldDenyAccess_whenApiKeyLacksRequiredScope() throws Throwable {
            // Given
            ApiKeyPrincipal principal =
                    new ApiKeyPrincipal(TEST_USER, KEY_ID, Set.of(ApiKeyScope.WRITE));
            setAuthentication(principal);
            setUpMethodStubbing();

            // When & Then
            assertThatThrownBy(() -> aspect.enforceScope(joinPoint, readAnnotation))
                    .isInstanceOf(ForbiddenException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_020);

            then(joinPoint).should(never()).proceed();
            then(auditLogService)
                    .should()
                    .logFailure(
                            eq(USER_ID),
                            eq(AuditAction.API_KEY_SCOPE_DENIED),
                            eq(ResourceType.API_KEY),
                            eq(KEY_ID.toString()),
                            eq("READ"));
        }

        @Test
        @DisplayName("shouldAllowAccess_whenApiKeyHasMultipleScopesIncludingRequired")
        void shouldAllowAccess_whenApiKeyHasMultipleScopesIncludingRequired() throws Throwable {
            // Given
            ApiKeyPrincipal principal =
                    new ApiKeyPrincipal(
                            TEST_USER, KEY_ID, Set.of(ApiKeyScope.READ, ApiKeyScope.WRITE));
            setAuthentication(principal);
            setUpMethodStubbing();

            // When & Then - readEndpoint requires READ, so this should pass
            given(joinPoint.proceed()).willReturn("result");
            Object result = aspect.enforceScope(joinPoint, readAnnotation);
            assertThat(result).isEqualTo("result");
            then(joinPoint).should().proceed();
        }
    }

    @Nested
    @DisplayName("Non-API Key authentication")
    class NonApiKeyAuth {

        @Test
        @DisplayName("shouldBypassCheck_whenPrincipalIsCurrentUser")
        void shouldBypassCheck_whenPrincipalIsCurrentUser() throws Throwable {
            // Given
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            "test@example.com",
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            SecurityContextHolder.setContext(context);
            given(joinPoint.proceed()).willReturn("result");

            // When
            Object result = aspect.enforceScope(joinPoint, readAnnotation);

            // Then
            assertThat(result).isEqualTo("result");
            then(joinPoint).should().proceed();
            then(auditLogService).should(never()).logFailure(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("shouldPassThrough_whenNoAuthentication")
        void shouldPassThrough_whenNoAuthentication() throws Throwable {
            // Given
            SecurityContextHolder.clearContext();
            given(joinPoint.proceed()).willReturn("result");

            // When
            Object result = aspect.enforceScope(joinPoint, readAnnotation);

            // Then
            assertThat(result).isEqualTo("result");
            then(joinPoint).should().proceed();
        }
    }

    static class TestController {
        @RequiresApiKeyScope(ApiKeyScope.READ)
        public String readEndpoint() {
            return "result";
        }

        @RequiresApiKeyScope(ApiKeyScope.WRITE)
        public String writeEndpoint() {
            return "result";
        }
    }
}
