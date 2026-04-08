package com.ahogek.cttserver.common.ratelimit;

import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.exception.TooManyRequestsException;
import com.ahogek.cttserver.common.ratelimit.core.RateLimitKeyFactory;
import com.ahogek.cttserver.common.ratelimit.core.RedisRateLimiter;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitAspectTest {

    private RateLimitKeyFactory mockKeyFactory;
    private RedisRateLimiter mockRateLimiter;
    private AuditLogService mockAuditLog;
    private CurrentUserProvider mockUserProvider;
    private RateLimitAspect aspect;
    private ProceedingJoinPoint mockJoinPoint;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        mockKeyFactory = mock(RateLimitKeyFactory.class);
        mockRateLimiter = mock(RedisRateLimiter.class);
        mockAuditLog = mock(AuditLogService.class);
        mockUserProvider = mock(CurrentUserProvider.class);
        SpelExpressionResolver mockSpelResolver = mock(SpelExpressionResolver.class);
        aspect =
                new RateLimitAspect(
                        mockKeyFactory,
                        mockRateLimiter,
                        mockAuditLog,
                        mockUserProvider,
                        mockSpelResolver);
        mockJoinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature mockSignature = mock(MethodSignature.class);

        when(mockJoinPoint.getSignature()).thenReturn(mockSignature);
        when(mockSignature.getDeclaringType()).thenReturn(TestController.class);
        when(mockSignature.getMethod()).thenReturn(getTestMethod());
    }

    @Test
    void intercept_whenAllowed_proceedsNormally() throws Throwable {
        RateLimit rateLimit = createRateLimit(10, 60);
        when(mockKeyFactory.generateKey(any(), anyString(), any())).thenReturn("rate_limit:test");
        when(mockRateLimiter.isAllowed(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(mockJoinPoint.proceed()).thenReturn("success");

        Object result = aspect.interceptSingle(mockJoinPoint, rateLimit);

        assertThat(result).isEqualTo("success");
        verify(mockJoinPoint).proceed();
        verify(mockAuditLog, never()).logFailure(any(), any(), any(), any(), any());
    }

    @Test
    void intercept_whenRateLimitExceeded_throwsExceptionAndLogsAudit() {
        RateLimit rateLimit = createRateLimit(5, 300);
        UUID userId = UUID.randomUUID();
        CurrentUser user =
                new CurrentUser(
                        userId,
                        "test@example.com",
                        UserStatus.ACTIVE,
                        Set.of("USER"),
                        CurrentUser.AuthenticationType.WEB_SESSION);

        when(mockKeyFactory.generateKey(any(), anyString(), any()))
                .thenReturn("rate_limit:ip:TestController.testMethod");
        when(mockRateLimiter.isAllowed(anyString(), anyInt(), anyInt())).thenReturn(false);
        when(mockUserProvider.getCurrentUser()).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> aspect.interceptSingle(mockJoinPoint, rateLimit))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Too many requests");

        verify(mockAuditLog)
                .logFailure(
                        userId,
                        com.ahogek.cttserver.audit.enums.AuditAction.RATE_LIMIT_EXCEEDED,
                        com.ahogek.cttserver.audit.enums.ResourceType.UNKNOWN,
                        "rate_limit:ip:TestController.testMethod",
                        "Rate limit exceeded for IP");
    }

    private Method getTestMethod() throws NoSuchMethodException {
        return TestController.class.getMethod("testMethod");
    }

    private RateLimit createRateLimit(int limit, int windowSeconds) {
        return new RateLimit() {
            @Override
            public RateLimitType type() {
                return RateLimitType.IP;
            }

            @Override
            public String keyExpression() {
                return "";
            }

            @Override
            public int limit() {
                return limit;
            }

            @Override
            public int windowSeconds() {
                return windowSeconds;
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return RateLimit.class;
            }
        };
    }

    static class TestController {
        public String testMethod() {
            return "test";
        }
    }
}
