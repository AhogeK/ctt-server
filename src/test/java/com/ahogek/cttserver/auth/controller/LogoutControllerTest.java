package com.ahogek.cttserver.auth.controller;

import com.ahogek.cttserver.auth.service.LogoutService;
import com.ahogek.cttserver.common.BaseControllerSliceTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.lang.reflect.Method;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@BaseControllerSliceTest(LogoutController.class)
@DisplayName("LogoutController Web MVC Tests")
class LogoutControllerTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private LogoutService logoutService;

    @Nested
    @DisplayName("POST /api/v1/auth/logout - Happy Path")
    class HappyPathTests {

        @Test
        @DisplayName("Should logout successfully with valid token")
        void shouldLogoutSuccessfully_withValidToken() {
            // Given
            String refreshToken = "valid-refresh-token-abc123";
            String userId = "550e8400-e29b-41d4-a716-446655440000";
            String request =
                    """
                {
                    "refreshToken": "%s"
                }
                """
                            .formatted(refreshToken);

            // When & Then
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/logout")
                                    .with(csrf())
                                    .with(jwt().jwt(jwt -> jwt.subject(userId)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.success")
                    .isEqualTo(true);

            verify(logoutService).logout(UUID.fromString(userId), refreshToken);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout - Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should return 400 when refresh token is blank")
        void shouldReturn400_whenRefreshTokenIsBlank() {
            // Given
            String userId = "550e8400-e29b-41d4-a716-446655440000";
            String request =
                    """
                {
                    "refreshToken": ""
                }
                """;

            // When & Then
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/logout")
                                    .with(csrf())
                                    .with(jwt().jwt(jwt -> jwt.subject(userId)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_003");
        }

        @Test
        @DisplayName("Should return 400 when refresh token is null")
        void shouldReturn400_whenRefreshTokenIsNull() {
            // Given
            String userId = "550e8400-e29b-41d4-a716-446655440000";
            String request =
                    """
                {
                    "refreshToken": null
                }
                """;

            // When & Then
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/logout")
                                    .with(csrf())
                                    .with(jwt().jwt(jwt -> jwt.subject(userId)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_003");
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout - Security Tests")
    class SecurityTests {

        @Test
        @DisplayName("Should require authentication (protected endpoint)")
        void shouldRequireAuthentication() {
            // Given
            String request =
                    """
                {
                    "refreshToken": "some-token"
                }
                """;

            // When & Then - Logout endpoint requires authentication via @AuthenticationPrincipal
            // Jwt
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/logout")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                    .hasStatus(401);
        }

        @Test
        @DisplayName("Should NOT have rate limiting annotation")
        void shouldNotHaveRateLimiting() throws NoSuchMethodException {
            Method logoutMethod =
                    LogoutController.class.getMethod(
                            "logout",
                            org.springframework.security.oauth2.jwt.Jwt.class,
                            com.ahogek.cttserver.auth.dto.LogoutRequest.class);

            com.ahogek.cttserver.common.ratelimit.RateLimit rateLimitAnnotation =
                    logoutMethod.getAnnotation(
                            com.ahogek.cttserver.common.ratelimit.RateLimit.class);

            assertThat(rateLimitAnnotation).isNull();
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout - Swagger Documentation Tests")
    class SwaggerDocumentationTests {

        @Test
        @DisplayName("Should have Swagger annotations")
        void shouldHaveSwaggerAnnotations() throws NoSuchMethodException {
            Method logoutMethod =
                    LogoutController.class.getMethod(
                            "logout",
                            org.springframework.security.oauth2.jwt.Jwt.class,
                            com.ahogek.cttserver.auth.dto.LogoutRequest.class);

            Operation operation = logoutMethod.getAnnotation(Operation.class);
            assertThat(operation).isNotNull();
            assertThat(operation.summary()).isEqualTo("Logout user");
            assertThat(operation.description())
                    .isEqualTo(
                            "Revoke refresh token and terminate session (requires authentication)");

            ApiResponses apiResponses = logoutMethod.getAnnotation(ApiResponses.class);
            assertThat(apiResponses).isNotNull();
            assertThat(apiResponses.value()).hasSize(3);

            assertThat(apiResponses.value()[0].responseCode()).isEqualTo("200");
            assertThat(apiResponses.value()[1].responseCode()).isEqualTo("400");
            assertThat(apiResponses.value()[2].responseCode()).isEqualTo("401");
        }
    }
}
