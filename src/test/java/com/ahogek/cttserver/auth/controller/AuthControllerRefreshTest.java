package com.ahogek.cttserver.auth.controller;

import com.ahogek.cttserver.auth.AuthController;
import com.ahogek.cttserver.auth.dto.LoginResponse;
import com.ahogek.cttserver.auth.service.TokenRefreshService;
import com.ahogek.cttserver.auth.service.UserLoginService;
import com.ahogek.cttserver.common.BaseControllerSliceTest;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.user.service.UserService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Token refresh endpoint tests for AuthController.
 *
 * <p>Tests the POST /api/v1/auth/refresh endpoint with various scenarios including valid tokens,
 * invalid tokens, expired tokens, reuse detection, and validation errors.
 *
 * @author AhogeK
 * @since 2026-04-02
 */
@BaseControllerSliceTest(AuthController.class)
@DisplayName("AuthController Token Refresh Tests")
class AuthControllerRefreshTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private UserService userService;

    @MockitoBean private UserLoginService userLoginService;

    @MockitoBean private TokenRefreshService tokenRefreshService;

    @Test
    @WithMockUser
    @DisplayName("Should return new tokens when refresh token is valid")
    void shouldReturnNewTokens_whenRefreshTokenIsValid() {
        UUID userId = UUID.randomUUID();
        LoginResponse mockResponse =
                new LoginResponse(userId, "new-access-token", "new-refresh-token", 3600L);

        given(tokenRefreshService.refresh(any(), any(), any())).willReturn(mockResponse);

        String request =
                """
            {
                "refreshToken": "valid-refresh-token"
            }
            """;

        var result =
                mvc.post()
                        .uri("/api/v1/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)
                        .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.data.userId").isEqualTo(userId.toString());
        assertThat(result)
                .bodyJson()
                .extractingPath("$.data.accessToken")
                .isEqualTo("new-access-token");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.data.refreshToken")
                .isEqualTo("new-refresh-token");
        assertThat(result).bodyJson().extractingPath("$.data.expiresIn").isEqualTo(3600);
    }

    @Test
    @WithMockUser
    @DisplayName("Should return 401 when refresh token is invalid")
    void shouldReturn401_whenRefreshTokenInvalid() {
        given(tokenRefreshService.refresh(any(), any(), any()))
                .willThrow(new UnauthorizedException(ErrorCode.AUTH_003, "Invalid refresh token"));

        String request =
                """
            {
                "refreshToken": "invalid-refresh-token"
            }
            """;

        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/refresh")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_003");
    }

    @Test
    @WithMockUser
    @DisplayName("Should return 401 when refresh token expired")
    void shouldReturn401_whenRefreshTokenExpired() {
        given(tokenRefreshService.refresh(any(), any(), any()))
                .willThrow(
                        new UnauthorizedException(ErrorCode.AUTH_007, "Refresh token has expired"));

        String request =
                """
            {
                "refreshToken": "expired-refresh-token"
            }
            """;

        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/refresh")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_007");
    }

    @Test
    @WithMockUser
    @DisplayName("Should return 403 when refresh token reuse detected")
    void shouldReturn403_whenRefreshTokenReuseDetected() {
        given(tokenRefreshService.refresh(any(), any(), any()))
                .willThrow(
                        new ForbiddenException(
                                ErrorCode.AUTH_009,
                                "Security breach: Refresh token reuse detected"));

        String request =
                """
            {
                "refreshToken": "revoked-refresh-token"
            }
            """;

        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/refresh")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .hasStatus(403)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("AUTH_009");
    }

    @Test
    @WithMockUser
    @DisplayName("Should return 400 when refresh token is blank")
    void shouldReturn400_whenRefreshTokenBlank() {
        String request =
                """
            {
                "refreshToken": ""
            }
            """;

        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/refresh")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("COMMON_003");
    }
}
