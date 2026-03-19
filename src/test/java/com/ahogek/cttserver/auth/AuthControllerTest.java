package com.ahogek.cttserver.auth;

import com.ahogek.cttserver.common.BaseControllerSliceTest;
import com.ahogek.cttserver.user.service.UserService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

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
                        "password": "Test@1234"
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
                        "password": "Test@1234"
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
}
