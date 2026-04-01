package com.ahogek.cttserver.auth.controller;

import com.ahogek.cttserver.auth.AuthController;
import com.ahogek.cttserver.auth.service.UserLoginService;
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
 * Malformed input handling tests for AuthController login endpoint.
 *
 * <p>Tests edge cases in JSON parsing and validation to ensure graceful error handling.
 *
 * @author AhogeK
 * @since 2026-04-02
 */
@BaseControllerSliceTest(AuthController.class)
@DisplayName("AuthController Malformed Input Tests")
class AuthControllerMalformedInputTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private UserService userService;

    @MockitoBean private UserLoginService userLoginService;

    @Nested
    @DisplayName("POST /api/v1/auth/login - Malformed Input Handling")
    class MalformedInputTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 400 when JSON has trailing comma")
        void shouldReturn400_whenTrailingComma() {
            String request =
                    """
                    {
                        "email": "test@example.com",
                        "password": "Test@1234",
                        "deviceId": "device-123",
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/login")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                    .hasStatus(400);
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 with COMMON_003 when password field is missing")
        void shouldReturn400_whenMissingRequiredField() {
            String request =
                    """
                    {
                        "email": "test@example.com",
                        "deviceId": "device-123"
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/login")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_003");
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 when email field has wrong type (number instead of string)")
        void shouldReturn400_whenWrongType() {
            String request =
                    """
                    {
                        "email": 12345,
                        "password": "Test@1234",
                        "deviceId": "device-123"
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/login")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                    .hasStatus(400);
        }

        @Test
        @WithMockUser
        @DisplayName("Should handle unknown field gracefully (Spring Boot ignores by default)")
        void shouldHandleUnknownFieldGracefully() {
            String request =
                    """
                    {
                        "email": "test@example.com",
                        "password": "Test@1234",
                        "deviceId": "device-123",
                        "unknownField": "value"
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/login")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                    .hasStatusOk();
        }
    }
}