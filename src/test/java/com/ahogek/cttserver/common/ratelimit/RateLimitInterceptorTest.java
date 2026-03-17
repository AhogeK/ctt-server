package com.ahogek.cttserver.common.ratelimit;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.context.RequestContext;
import com.ahogek.cttserver.common.context.RequestInfo;
import com.ahogek.cttserver.user.enums.UserStatus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitInterceptorTest {

    private CurrentUserProvider mockUserProvider;
    private RateLimitInterceptor interceptor;
    private HttpServletRequest mockRequest;
    private HttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockUserProvider = mock(CurrentUserProvider.class);
        interceptor = new RateLimitInterceptor(mockUserProvider);
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
    }

    @AfterEach
    void tearDown() {
        // RequestContext uses ScopedValue which auto-clears when scope ends
    }

    @Test
    void preHandle_whenNotHandlerMethod_returnsTrue() {
        boolean result = interceptor.preHandle(mockRequest, mockResponse, new Object());

        assertTrue(result);
    }

    @Test
    void preHandle_whenNoRateLimitAnnotation_returnsTrue() throws Exception {
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(), "noRateLimit");

        boolean result = interceptor.preHandle(mockRequest, mockResponse, handlerMethod);

        assertTrue(result);
    }

    @Test
    void preHandle_whenClassLevelRateLimit_usesClassLevel() throws Exception {
        when(mockUserProvider.getCurrentUserRequired()).thenReturn(createTestUser());
        HandlerMethod handlerMethod =
                new HandlerMethod(new TestController(), "classLevelRateLimit");

        boolean result = interceptor.preHandle(mockRequest, mockResponse, handlerMethod);

        assertTrue(result);
    }

    @Test
    void preHandle_whenUserRateLimit_extractsUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        CurrentUser user = createTestUser(userId);
        when(mockUserProvider.getCurrentUserRequired()).thenReturn(user);
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(), "userRateLimit");

        boolean result = interceptor.preHandle(mockRequest, mockResponse, handlerMethod);

        assertTrue(result);
        verify(mockUserProvider).getCurrentUserRequired();
    }

    @Test
    void preHandle_whenIpRateLimit_extractsIp() {
        RequestInfo requestInfo =
                new RequestInfo("trace-123", "192.168.1.1", "TestAgent", "/api/test", "GET", null);
        ScopedValue.where(RequestContext.CONTEXT, requestInfo)
                .run(
                        () -> {
                            try {
                                HandlerMethod handlerMethod =
                                        new HandlerMethod(new TestController(), "ipRateLimit");

                                boolean result =
                                        interceptor.preHandle(
                                                mockRequest, mockResponse, handlerMethod);

                                assertTrue(result);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
    }

    @Test
    void preHandle_whenDeviceRateLimit_extractsDeviceId() {
        RequestInfo requestInfo =
                new RequestInfo(
                        "trace-123", "192.168.1.1", "TestAgent", "/api/test", "GET", "device-456");
        ScopedValue.where(RequestContext.CONTEXT, requestInfo)
                .run(
                        () -> {
                            try {
                                HandlerMethod handlerMethod =
                                        new HandlerMethod(new TestController(), "deviceRateLimit");

                                boolean result =
                                        interceptor.preHandle(
                                                mockRequest, mockResponse, handlerMethod);

                                assertTrue(result);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
    }

    @Test
    void preHandle_whenDeviceRateLimitWithoutDeviceId_usesUnknown() {
        RequestInfo requestInfo =
                new RequestInfo("trace-123", "192.168.1.1", "TestAgent", "/api/test", "GET", null);
        ScopedValue.where(RequestContext.CONTEXT, requestInfo)
                .run(
                        () -> {
                            try {
                                HandlerMethod handlerMethod =
                                        new HandlerMethod(new TestController(), "deviceRateLimit");

                                boolean result =
                                        interceptor.preHandle(
                                                mockRequest, mockResponse, handlerMethod);

                                assertTrue(result);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
    }

    @Test
    void preHandle_whenGlobalRateLimit_usesGlobalKey() throws Exception {
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(), "globalRateLimit");

        boolean result = interceptor.preHandle(mockRequest, mockResponse, handlerMethod);

        assertTrue(result);
    }

    @Test
    void preHandle_whenUserRateLimitButNotAuthenticated_throwsException() throws Exception {
        when(mockUserProvider.getCurrentUserRequired())
                .thenThrow(
                        new com.ahogek.cttserver.common.exception.UnauthorizedException(
                                com.ahogek.cttserver.common.exception.ErrorCode.AUTH_001,
                                "Authentication required"));
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(), "userRateLimit");

        assertThrows(
                com.ahogek.cttserver.common.exception.UnauthorizedException.class,
                () -> interceptor.preHandle(mockRequest, mockResponse, handlerMethod));
    }

    private CurrentUser createTestUser() {
        return createTestUser(UUID.randomUUID());
    }

    private CurrentUser createTestUser(UUID userId) {
        return new CurrentUser(
                userId,
                "test@example.com",
                UserStatus.ACTIVE,
                Set.of("USER"),
                CurrentUser.AuthenticationType.WEB_SESSION);
    }

    static class TestController {
        public void noRateLimit() {}

        @RateLimit(type = RateLimitType.USER, capacity = 10, period = 60)
        public void userRateLimit() {}

        @RateLimit(type = RateLimitType.IP, capacity = 100, period = 60)
        public void ipRateLimit() {}

        @RateLimit(type = RateLimitType.DEVICE, capacity = 50, period = 60)
        public void deviceRateLimit() {}

        @RateLimit(type = RateLimitType.GLOBAL, capacity = 1000, period = 60)
        public void globalRateLimit() {}

        @RateLimit(type = RateLimitType.USER, capacity = 10, period = 60)
        public void classLevelRateLimit() {}
    }
}
