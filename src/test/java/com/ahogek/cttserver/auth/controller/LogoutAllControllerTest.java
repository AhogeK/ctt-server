package com.ahogek.cttserver.auth.controller;

import com.ahogek.cttserver.auth.AuthController;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.auth.service.LogoutService;
import com.ahogek.cttserver.auth.service.PasswordResetService;
import com.ahogek.cttserver.auth.service.TokenRefreshService;
import com.ahogek.cttserver.auth.service.UserLoginService;
import com.ahogek.cttserver.auth.filter.TermsCheckFilter;
import com.ahogek.cttserver.common.BaseControllerSliceTest;
import com.ahogek.cttserver.common.ratelimit.RateLimit;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.service.UserService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@BaseControllerSliceTest(
        value = AuthController.class,
        excludeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {TermsCheckFilter.class}))
@DisplayName("LogoutAllController Web MVC Tests")
class LogoutAllControllerTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private UserService userService;

    @MockitoBean private UserLoginService userLoginService;

    @MockitoBean private TokenRefreshService tokenRefreshService;

    @MockitoBean private LogoutService logoutService;

    @MockitoBean private PasswordResetService passwordResetService;

    private Authentication createAuth(
        UUID userId, Set<String> authorities) {
        CurrentUser currentUser =
                new CurrentUser(
                        userId,
                    "test@example.com",
                    UserStatus.ACTIVE,
                        authorities,
                        CurrentUser.AuthenticationType.WEB_SESSION);
        return new UsernamePasswordAuthenticationToken(
                currentUser, null, authorities.stream().map(SimpleGrantedAuthority::new).toList());
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout-all - Happy Path")
    class HappyPathTests {

        @Test
        @DisplayName("Should logout all devices successfully with valid JWT")
        void shouldLogoutAllDevicesSuccessfully_withValidJwt() {
            UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
            Authentication auth =
                    createAuth(userId, Set.of("ROLE_USER"));

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/logout-all")
                                    .with(csrf())
                                    .with(authentication(auth)))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.success")
                    .isEqualTo(true);

            verify(logoutService).logoutAll(userId);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout-all - Security Tests")
    class SecurityTests {

        @Test
        @DisplayName("Should require authentication (protected endpoint)")
        void shouldRequireAuthentication() {
            assertThat(mvc.post().uri("/api/v1/auth/logout-all").with(csrf())).hasStatus(401);
        }

        @Test
        @DisplayName("Should use USER_ID rate limiting (not IP)")
        void shouldUseUserIdRateLimiting() throws NoSuchMethodException {
            Method logoutAllMethod = AuthController.class.getMethod("logoutAll", CurrentUser.class);

            RateLimit rateLimitAnnotation = logoutAllMethod.getAnnotation(RateLimit.class);

            assertThat(rateLimitAnnotation).isNotNull();
            assertThat(rateLimitAnnotation.type()).isEqualTo(RateLimitType.USER);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout-all - Swagger Documentation Tests")
    class SwaggerDocumentationTests {

        @Test
        @DisplayName("Should have Swagger annotations")
        void shouldHaveSwaggerAnnotations() throws NoSuchMethodException {
            Method logoutAllMethod = AuthController.class.getMethod("logoutAll", CurrentUser.class);

            Operation operation = logoutAllMethod.getAnnotation(Operation.class);
            assertThat(operation).isNotNull();
            assertThat(operation.summary()).isEqualTo("Global logout (Kill Switch)");
            assertThat(operation.description()).contains("Protected endpoint");

            ApiResponses apiResponses = logoutAllMethod.getAnnotation(ApiResponses.class);
            assertThat(apiResponses).isNotNull();
            assertThat(apiResponses.value()).hasSize(3);

            assertThat(apiResponses.value()[0].responseCode()).isEqualTo("200");
            assertThat(apiResponses.value()[1].responseCode()).isEqualTo("401");
            assertThat(apiResponses.value()[2].responseCode()).isEqualTo("429");
        }
    }
}
