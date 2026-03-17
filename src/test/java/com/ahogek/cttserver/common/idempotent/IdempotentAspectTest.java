package com.ahogek.cttserver.common.idempotent;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.user.enums.UserStatus;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdempotentAspectTest {

    private CurrentUserProvider mockUserProvider;
    private IdempotentAspect aspect;
    private ProceedingJoinPoint mockJoinPoint;

    @BeforeEach
    void setUp() {
        mockUserProvider = mock(CurrentUserProvider.class);
        aspect = new IdempotentAspect(mockUserProvider);
        mockJoinPoint = mock(ProceedingJoinPoint.class);
    }

    @Test
    void around_withAuthenticatedUser_includesUserId() throws Throwable {
        UUID userId = UUID.randomUUID();
        CurrentUser user = createTestUser(userId);
        when(mockUserProvider.getCurrentUser()).thenReturn(Optional.of(user));
        when(mockJoinPoint.proceed()).thenReturn("success");
        when(mockJoinPoint.getSignature())
                .thenReturn(
                        new org.aspectj.lang.Signature() {
                            @Override
                            public String toShortString() {
                                return "TestController.testMethod()";
                            }

                            @Override
                            public String toLongString() {
                                return "TestController.testMethod()";
                            }

                            @Override
                            public String getName() {
                                return "testMethod";
                            }

                            @Override
                            public int getModifiers() {
                                return 0;
                            }

                            @Override
                            public Class<?> getDeclaringType() {
                                return TestController.class;
                            }

                            @Override
                            public String getDeclaringTypeName() {
                                return "TestController";
                            }
                        });

        Idempotent idempotent = createIdempotent("", 5, TimeUnit.SECONDS);

        Object result = aspect.around(mockJoinPoint, idempotent);

        assertEquals("success", result);
        verify(mockJoinPoint).proceed();
    }

    @Test
    void around_withAnonymousUser_usesAnonymousKey() throws Throwable {
        when(mockUserProvider.getCurrentUser()).thenReturn(Optional.empty());
        when(mockJoinPoint.proceed()).thenReturn("success");
        when(mockJoinPoint.getSignature())
                .thenReturn(
                        new org.aspectj.lang.Signature() {
                            @Override
                            public String toShortString() {
                                return "TestController.publicMethod()";
                            }

                            @Override
                            public String toLongString() {
                                return "TestController.publicMethod()";
                            }

                            @Override
                            public String getName() {
                                return "publicMethod";
                            }

                            @Override
                            public int getModifiers() {
                                return 0;
                            }

                            @Override
                            public Class<?> getDeclaringType() {
                                return TestController.class;
                            }

                            @Override
                            public String getDeclaringTypeName() {
                                return "TestController";
                            }
                        });

        Idempotent idempotent = createIdempotent("", 5, TimeUnit.SECONDS);

        Object result = aspect.around(mockJoinPoint, idempotent);

        assertEquals("success", result);
        verify(mockJoinPoint).proceed();
    }

    @Test
    void around_withCustomKey_usesCustomKey() throws Throwable {
        when(mockUserProvider.getCurrentUser()).thenReturn(Optional.empty());
        when(mockJoinPoint.proceed()).thenReturn("success");
        when(mockJoinPoint.getSignature())
                .thenReturn(
                        new org.aspectj.lang.Signature() {
                            @Override
                            public String toShortString() {
                                return "TestController.customKeyMethod()";
                            }

                            @Override
                            public String toLongString() {
                                return "TestController.customKeyMethod()";
                            }

                            @Override
                            public String getName() {
                                return "customKeyMethod";
                            }

                            @Override
                            public int getModifiers() {
                                return 0;
                            }

                            @Override
                            public Class<?> getDeclaringType() {
                                return TestController.class;
                            }

                            @Override
                            public String getDeclaringTypeName() {
                                return "TestController";
                            }
                        });

        Idempotent idempotent = createIdempotent("custom-key", 10, TimeUnit.MINUTES);

        Object result = aspect.around(mockJoinPoint, idempotent);

        assertEquals("success", result);
        verify(mockJoinPoint).proceed();
    }

    @Test
    void around_whenProceedThrowsException_propagatesException() throws Throwable {
        when(mockUserProvider.getCurrentUser()).thenReturn(Optional.empty());
        when(mockJoinPoint.proceed()).thenThrow(new RuntimeException("Test exception"));
        when(mockJoinPoint.getSignature())
                .thenReturn(
                        new org.aspectj.lang.Signature() {
                            @Override
                            public String toShortString() {
                                return "TestController.failingMethod()";
                            }

                            @Override
                            public String toLongString() {
                                return "TestController.failingMethod()";
                            }

                            @Override
                            public String getName() {
                                return "failingMethod";
                            }

                            @Override
                            public int getModifiers() {
                                return 0;
                            }

                            @Override
                            public Class<?> getDeclaringType() {
                                return TestController.class;
                            }

                            @Override
                            public String getDeclaringTypeName() {
                                return "TestController";
                            }
                        });

        Idempotent idempotent = createIdempotent("", 5, TimeUnit.SECONDS);

        assertThrows(RuntimeException.class, () -> aspect.around(mockJoinPoint, idempotent));
    }

    private CurrentUser createTestUser(UUID userId) {
        return new CurrentUser(
                userId,
                "test@example.com",
                UserStatus.ACTIVE,
                Set.of("USER"),
                CurrentUser.AuthenticationType.WEB_SESSION);
    }

    private Idempotent createIdempotent(String key, long expire, TimeUnit unit) {
        return new Idempotent() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public long expire() {
                return expire;
            }

            @Override
            public TimeUnit unit() {
                return unit;
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return Idempotent.class;
            }
        };
    }

    static class TestController {
        public void testMethod() {}
    }
}
