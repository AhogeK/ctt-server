package com.ahogek.cttserver.auth.controller;

import com.ahogek.cttserver.auth.AuthController;
import com.ahogek.cttserver.auth.service.LogoutService;
import com.ahogek.cttserver.auth.service.TokenRefreshService;
import com.ahogek.cttserver.auth.service.UserLoginService;
import com.ahogek.cttserver.common.BaseControllerSliceTest;
import com.ahogek.cttserver.user.service.UserService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@BaseControllerSliceTest(AuthController.class)
@DisplayName("AuthController Validation Tests")
class AuthControllerValidationTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private UserLoginService userLoginService;

    @MockitoBean private UserService userService;

    @MockitoBean private TokenRefreshService tokenRefreshService;

    @MockitoBean private LogoutService logoutService;

    @Nested
    @DisplayName("POST /api/v1/auth/login - Parameter Validation")
    class LoginValidationTests {

        @ParameterizedTest(name = "{0}")
        @WithMockUser
        @DisplayName("Should return 400 for invalid login request")
        @MethodSource(
                "com.ahogek.cttserver.auth.controller.AuthControllerValidationTest#invalidLoginRequests")
        void shouldReturn400_forInvalidLoginRequest(String testName, String request) {
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

    static Stream<Arguments> invalidLoginRequests() {
        return Stream.of(
                Arguments.of(
                        "Invalid email format",
                        """
                {
                    "email": "not-an-email",
                    "password": "Test@1234",
                    "deviceId": "device-123"
                }
                """),
                Arguments.of(
                        "Weak password",
                        """
                {
                    "email": "test@example.com",
                    "password": "weak",
                    "deviceId": "device-123"
                }
                """),
                Arguments.of(
                        "Blank deviceId",
                        """
                {
                    "email": "test@example.com",
                    "password": "Test@1234",
                    "deviceId": ""
                }
                """),
                Arguments.of("Empty request body", "{}"),
                Arguments.of(
                        "Null fields",
                        """
                {
                    "email": null,
                    "password": null,
                    "deviceId": null
                }
                """));
    }
}
