package com.ahogek.cttserver.auth.controller;

import com.ahogek.cttserver.auth.AuthController;
import com.ahogek.cttserver.auth.service.LogoutService;
import com.ahogek.cttserver.auth.service.PasswordResetService;
import com.ahogek.cttserver.auth.service.TokenRefreshService;
import com.ahogek.cttserver.auth.service.UserLoginService;
import com.ahogek.cttserver.common.BaseControllerSliceTest;
import com.ahogek.cttserver.user.service.UserService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Forgot password endpoint tests for AuthController.
 *
 * <p>Tests the POST /api/v1/auth/forgot-password endpoint with various scenarios including valid
 * requests, invalid emails, and accessibility without authentication.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-07
 */
@BaseControllerSliceTest(AuthController.class)
@DisplayName("AuthController Forgot Password Tests")
class AuthControllerForgotPasswordTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private UserService userService;

    @MockitoBean private UserLoginService userLoginService;

    @MockitoBean private TokenRefreshService tokenRefreshService;

    @MockitoBean private LogoutService logoutService;

    @MockitoBean private PasswordResetService passwordResetService;

    @Test
    @WithMockUser
    @DisplayName("Should return 200 when email is valid")
    void shouldReturn200_whenEmailIsValid() {
        String request =
                """
            {
                "email": "test@example.com"
            }
            """;

        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/forgot-password")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.success")
                .isEqualTo(true);

        then(passwordResetService).should().requestReset(any(), any(), any());
    }

    @Test
    @WithMockUser
    @DisplayName("Should return 400 when email is invalid")
    void shouldReturn400_whenEmailIsInvalid() {
        String request =
                """
            {
                "email": "invalid-email"
            }
            """;

        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/forgot-password")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("COMMON_003");

        then(passwordResetService).should(never()).requestReset(any(), any(), any());
    }

    @Test
    @WithMockUser
    @DisplayName("Should return 400 when email is blank")
    void shouldReturn400_whenEmailIsBlank() {
        String request =
                """
            {
                "email": ""
            }
            """;

        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/forgot-password")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("COMMON_003");

        then(passwordResetService).should(never()).requestReset(any(), any(), any());
    }
}
