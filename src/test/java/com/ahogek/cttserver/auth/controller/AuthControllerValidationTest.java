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

@BaseControllerSliceTest(AuthController.class)
@DisplayName("AuthController Validation Tests")
class AuthControllerValidationTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private UserLoginService userLoginService;

    @MockitoBean
    private UserService userService;

    @Nested
    @DisplayName("POST /api/v1/auth/login - Parameter Validation")
    class LoginValidationTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 400 when email format is invalid")
        void shouldReturn400_whenInvalidEmailFormat() {
            String request = """
                {
                    "email": "not-an-email",
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
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_003");
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 when password is weak")
        void shouldReturn400_whenWeakPassword() {
            String request = """
                {
                    "email": "test@example.com",
                    "password": "weak",
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
        @DisplayName("Should return 400 when deviceId is blank")
        void shouldReturn400_whenBlankDeviceId() {
            String request = """
                {
                    "email": "test@example.com",
                    "password": "Test@1234",
                    "deviceId": ""
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
        @DisplayName("Should return 400 when request body is empty")
        void shouldReturn400_whenEmptyRequestBody() {
            String request = "{}";

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
        @DisplayName("Should return 400 when fields are null")
        void shouldReturn400_whenNullFields() {
            String request = """
                {
                    "email": null,
                    "password": null,
                    "deviceId": null
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
    }
}