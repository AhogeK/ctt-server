package com.ahogek.cttserver.common.idempotent;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.idempotent.core.IdempotentLocker;
import com.ahogek.cttserver.common.util.SpelExpressionResolver;
import com.ahogek.cttserver.user.enums.UserStatus;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotentAspectTest {

    private IdempotentLocker mockLocker;
    private CurrentUserProvider mockUserProvider;
    private SpelExpressionResolver mockSpelResolver;
    private IdempotentAspect aspect;
    private ProceedingJoinPoint mockJoinPoint;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        mockLocker = mock(IdempotentLocker.class);
        mockUserProvider = mock(CurrentUserProvider.class);
        mockSpelResolver = mock(SpelExpressionResolver.class);
        aspect = new IdempotentAspect(mockLocker, mockUserProvider, mockSpelResolver);
        mockJoinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature mockSignature = mock(MethodSignature.class);

        when(mockJoinPoint.getSignature()).thenReturn(mockSignature);
        when(mockSignature.getDeclaringType()).thenReturn(TestController.class);
        when(mockSignature.getMethod()).thenReturn(getTestMethod());
    }

    @Test
    void intercept_whenLockAcquired_proceedsNormally() throws Throwable {
        Idempotent idempotent = createIdempotent("", "", true, 5, "Request in progress");
        UUID userId = UUID.randomUUID();
        CurrentUser user =
                new CurrentUser(
                        userId,
                        "test@example.com",
                        UserStatus.ACTIVE,
                        Set.of("USER"),
                        CurrentUser.AuthenticationType.WEB_SESSION);

        when(mockUserProvider.getCurrentUser()).thenReturn(Optional.of(user));
        when(mockLocker.tryLock(anyString(), anyLong())).thenReturn(true);
        when(mockJoinPoint.proceed()).thenReturn("success");

        Object result = aspect.intercept(mockJoinPoint, idempotent);

        assertThat(result).isEqualTo("success");
        verify(mockJoinPoint).proceed();
    }

    @Test
    void intercept_whenLockNotAcquired_throwsConflictException() throws Throwable {
        Idempotent idempotent =
                createIdempotent("REGISTER", "", false, 5, "Registration in progress");

        when(mockUserProvider.getCurrentUser()).thenReturn(Optional.empty());
        when(mockLocker.tryLock(anyString(), anyLong())).thenReturn(false);

        assertThatThrownBy(() -> aspect.intercept(mockJoinPoint, idempotent))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Registration in progress");

        verify(mockJoinPoint, never()).proceed();
    }

    @Test
    void intercept_whenExceptionThrown_releasesLockAndPropagates() throws Throwable {
        Idempotent idempotent = createIdempotent("", "", true, 5, "Processing");
        UUID userId = UUID.randomUUID();
        CurrentUser user =
                new CurrentUser(
                        userId,
                        "test@example.com",
                        UserStatus.ACTIVE,
                        Set.of("USER"),
                        CurrentUser.AuthenticationType.WEB_SESSION);

        when(mockUserProvider.getCurrentUser()).thenReturn(Optional.of(user));
        when(mockLocker.tryLock(anyString(), anyLong())).thenReturn(true);
        when(mockJoinPoint.proceed()).thenThrow(new RuntimeException("Business error"));

        assertThatThrownBy(() -> aspect.intercept(mockJoinPoint, idempotent))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Business error");

        verify(mockLocker).unlock(anyString());
    }

    @Test
    void intercept_withoutUserId_usesAnonymous() throws Throwable {
        Idempotent idempotent = createIdempotent("PUBLIC_API", "", false, 3, "Processing");

        when(mockUserProvider.getCurrentUser()).thenReturn(Optional.empty());
        when(mockLocker.tryLock(anyString(), anyLong())).thenReturn(true);
        when(mockJoinPoint.proceed()).thenReturn("success");

        Object result = aspect.intercept(mockJoinPoint, idempotent);

        assertThat(result).isEqualTo("success");
        verify(mockJoinPoint).proceed();
    }

    @Test
    void intercept_withSpelExpression_extractsValueFromArgument() throws Throwable {
        Idempotent idempotent = createIdempotent("", "#email", true, 5, "Processing");
        UUID userId = UUID.randomUUID();
        CurrentUser user =
                new CurrentUser(
                        userId,
                        "test@example.com",
                        UserStatus.ACTIVE,
                        Set.of("USER"),
                        CurrentUser.AuthenticationType.WEB_SESSION);

        when(mockUserProvider.getCurrentUser()).thenReturn(Optional.of(user));
        when(mockSpelResolver.resolve(any(), any(), any())).thenReturn("user@example.com");
        when(mockLocker.tryLock(anyString(), anyLong())).thenReturn(true);
        when(mockJoinPoint.proceed()).thenReturn("success");

        Object result = aspect.intercept(mockJoinPoint, idempotent);

        assertThat(result).isEqualTo("success");
        verify(mockJoinPoint).proceed();
    }

    @Test
    void intercept_withNullSpelExpression_returnsNullAndProceeds() throws Throwable {
        Idempotent idempotent = createIdempotent("", null, true, 5, "Processing");
        UUID userId = UUID.randomUUID();
        CurrentUser user =
                new CurrentUser(
                        userId,
                        "test@example.com",
                        UserStatus.ACTIVE,
                        Set.of("USER"),
                        CurrentUser.AuthenticationType.WEB_SESSION);

        when(mockUserProvider.getCurrentUser()).thenReturn(Optional.of(user));
        when(mockSpelResolver.resolve(any(), any(), any())).thenReturn(null);
        when(mockLocker.tryLock(anyString(), anyLong())).thenReturn(true);
        when(mockJoinPoint.proceed()).thenReturn("success");

        Object result = aspect.intercept(mockJoinPoint, idempotent);

        assertThat(result).isEqualTo("success");
        verify(mockJoinPoint).proceed();
    }

    private Method getTestMethod() throws NoSuchMethodException {
        return TestController.class.getMethod("testMethod", String.class);
    }

    private Idempotent createIdempotent(
            String prefix,
            String keyExpression,
            boolean includeUserId,
            long expireSeconds,
            String message) {
        return new Idempotent() {
            @Override
            public String prefix() {
                return prefix;
            }

            @Override
            public String keyExpression() {
                return keyExpression;
            }

            @Override
            public boolean includeUserId() {
                return includeUserId;
            }

            @Override
            public long expireSeconds() {
                return expireSeconds;
            }

            @Override
            public String message() {
                return message;
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return Idempotent.class;
            }
        };
    }

    static class TestController {
        public void testMethod(String requestId) {
            // Test method
        }
    }
}
