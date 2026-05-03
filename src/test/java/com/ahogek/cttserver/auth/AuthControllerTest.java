package com.ahogek.cttserver.auth;

import com.ahogek.cttserver.auth.dto.LoginResponse;
import com.ahogek.cttserver.auth.service.LogoutService;
import com.ahogek.cttserver.auth.service.PasswordResetService;
import com.ahogek.cttserver.auth.service.TokenRefreshService;
import com.ahogek.cttserver.auth.service.UserLoginService;
import com.ahogek.cttserver.common.BaseControllerSliceTest;
import com.ahogek.cttserver.user.service.UserService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * AuthController REST API tests.
 *
 * <p>Follows Developer Handbook patterns for controller slice testing using @MockitoBean.
 *
 * @see <a href="docs/developer-handbook.md#adding-protected-interfaces">Developer Handbook - Adding
 *     Protected Interfaces</a>
 */
@BaseControllerSliceTest(AuthController.class)
@DisplayName("AuthController REST API Tests")
class AuthControllerTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private UserService userService;

    @MockitoBean private UserLoginService userLoginService;

    @MockitoBean private TokenRefreshService tokenRefreshService;

    @MockitoBean private LogoutService logoutService;

    @MockitoBean private PasswordResetService passwordResetService;

    @Nested
    @DisplayName("POST /api/v1/auth/register - User Registration")
    class RegisterTests {

        @Test
        @WithMockUser
        @DisplayName("Should register user successfully and return 200 OK")
        void shouldRegisterUserSuccessfully() {
            String requestBody =
                    """
                    {
                        "email": "test@example.com",
                        "displayName": "TestUser",
                        "password": "Test@1234",
                        "termsVersion": "1.0.0"
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/register")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.success")
                    .isEqualTo(true);
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 Bad Request when email is invalid")
        void shouldReturn400_whenEmailIsInvalid() {
            String invalidRequest =
                    """
                    {
                        "email": "invalid-email",
                        "displayName": "TestUser",
                        "password": "Test@1234",
                        "termsVersion": "1.0.0"
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/register")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(invalidRequest))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_003");
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 Bad Request when password is weak")
        void shouldReturn400_whenPasswordIsWeak() {
            String weakPasswordRequest =
                    """
                    {
                        "email": "test@example.com",
                        "displayName": "TestUser",
                        "password": "weak"
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/register")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(weakPasswordRequest))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_003");
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 Bad Request when display name is blank")
        void shouldReturn400_whenDisplayNameIsBlank() {
            String blankNameRequest =
                    """
                    {
                        "email": "test@example.com",
                        "displayName": "",
                        "password": "Test@1234"
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/register")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(blankNameRequest))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_003");
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 Bad Request when request body is malformed JSON")
        void shouldReturn400_whenMalformedJson() {
            String malformedJson =
                    """
                    {
                        "email": "test@example.com",
                        "displayName": "TestUser",
                        "password": "Test@1234"
                    broken json
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/register")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(malformedJson))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_001");
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 Bad Request when required fields are missing")
        void shouldReturn400_whenRequiredFieldsMissing() {
            String missingFieldsRequest =
                    """
                    {
                        "displayName": "TestUser"
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/register")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(missingFieldsRequest))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_003");
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login - User Login")
    class LoginTests {

        @Test
        @WithMockUser
        @DisplayName("Should login successfully and return tokens")
        void shouldLoginSuccessfully_andReturnTokens() {
            String requestBody =
                    """
                    {
                        "email": "test@example.com",
                        "password": "Test@1234",
                        "deviceId": "device-123"
                    }
                    """;

            UUID userId = UUID.randomUUID();
            LoginResponse mockResponse =
                    new LoginResponse(userId, "access-token", "refresh-token", 3600L);

            BDDMockito.given(userLoginService.login(BDDMockito.any())).willReturn(mockResponse);

            var result =
                    mvc.post()
                            .uri("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.userId")
                    .isEqualTo(userId.toString());
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.accessToken")
                    .isEqualTo("access-token");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.refreshToken")
                    .isEqualTo("refresh-token");
            assertThat(result).bodyJson().extractingPath("$.data.expiresIn").isEqualTo(3600);
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 Bad Request when email is invalid")
        void shouldReturn400_whenEmailIsInvalid() {
            String invalidRequest =
                    """
                    {
                        "email": "invalid-email",
                        "password": "Test@1234"
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/login")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(invalidRequest))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_003");
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 Bad Request when password is blank")
        void shouldReturn400_whenPasswordIsBlank() {
            String blankPasswordRequest =
                    """
                    {
                        "email": "test@example.com",
                        "password": ""
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/login")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(blankPasswordRequest))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_003");
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 Bad Request when request body is malformed JSON")
        void shouldReturn400_whenMalformedJson() {
            String malformedJson =
                    """
                    {
                        "email": "test@example.com",
                        "password": "Test@1234"
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/login")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(malformedJson))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_001");
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 Bad Request when required fields are missing")
        void shouldReturn400_whenRequiredFieldsMissing() {
            String missingFieldsRequest =
                    """
                    {
                        "password": "Test@1234"
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/login")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(missingFieldsRequest))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_003");
        }
    }
}
