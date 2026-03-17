package com.ahogek.cttserver.common.ratelimit.core;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.context.ClientIdentity;
import com.ahogek.cttserver.common.context.RequestContext;
import com.ahogek.cttserver.common.context.RequestInfo;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;
import com.ahogek.cttserver.user.enums.UserStatus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitKeyFactoryTest {

    private CurrentUserProvider mockUserProvider;
    private RateLimitKeyFactory keyFactory;

    @BeforeEach
    void setUp() {
        mockUserProvider = mock(CurrentUserProvider.class);
        keyFactory = new RateLimitKeyFactory(mockUserProvider);
    }

    @AfterEach
    void tearDown() {
        // RequestContext uses ScopedValue which auto-clears when scope ends
    }

    @Test
    void generateKey_ipType_returnsIpKey() {
        RequestInfo requestInfo =
                new RequestInfo(
                        "trace-123",
                        "192.168.1.100",
                        "TestAgent",
                        "/api/test",
                        "GET",
                        ClientIdentity.empty());

        String key =
                ScopedValue.where(RequestContext.CONTEXT, requestInfo)
                        .call(
                                () ->
                                        keyFactory.generateKey(
                                                RateLimitType.IP, "TestController.test", null));

        assertThat(key).isEqualTo("rate_limit:ip:TestController.test:192.168.1.100");
    }

    @Test
    void generateKey_userType_returnsUserKey() {
        UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        CurrentUser user =
                new CurrentUser(
                        userId,
                        "test@example.com",
                        UserStatus.ACTIVE,
                        Set.of("USER"),
                        CurrentUser.AuthenticationType.WEB_SESSION);
        when(mockUserProvider.getCurrentUserRequired()).thenReturn(user);

        String key = keyFactory.generateKey(RateLimitType.USER, "UserController.getUser", null);

        assertThat(key).isEqualTo("rate_limit:user:UserController.getUser:" + userId);
    }

    @Test
    void generateKey_emailType_returnsEmailKey() {
        String key =
                keyFactory.generateKey(
                        RateLimitType.EMAIL, "AuthController.sendEmail", "user@example.com");

        assertThat(key).isEqualTo("rate_limit:email:AuthController.sendEmail:user@example.com");
    }

    @Test
    void generateKey_emailType_withNullSpElValue_throwsException() {
        assertThatThrownBy(
                        () ->
                                keyFactory.generateKey(
                                        RateLimitType.EMAIL, "AuthController.sendEmail", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SpEL expression evaluation failed");
    }

    @Test
    void generateKey_emailType_withBlankSpElValue_throwsException() {
        assertThatThrownBy(
                        () ->
                                keyFactory.generateKey(
                                        RateLimitType.EMAIL, "AuthController.sendEmail", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SpEL expression evaluation failed");
    }

    @Test
    void generateKey_apiType_returnsGlobalKey() {
        String key = keyFactory.generateKey(RateLimitType.API, "AggregateController.getData", null);

        assertThat(key).isEqualTo("rate_limit:api:AggregateController.getData");
    }
}
