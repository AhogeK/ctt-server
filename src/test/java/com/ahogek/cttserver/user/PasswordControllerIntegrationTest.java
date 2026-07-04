package com.ahogek.cttserver.user;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.filter.TermsCheckFilter;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.BaseControllerSliceTest;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.idempotent.IdempotentAspect;
import com.ahogek.cttserver.common.ratelimit.RateLimitAspect;
import com.ahogek.cttserver.user.controller.PasswordController;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.service.PasswordService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * PasswordController integration tests.
 *
 * <p>Covers the set-password endpoint for OAuth users. Uses MockMvc for HTTP testing with mocked
 * service dependencies.
 */
@BaseControllerSliceTest(
        value = PasswordController.class,
        excludeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                            RateLimitAspect.class,
                            IdempotentAspect.class,
                            TermsCheckFilter.class
                        }))
@DisplayName("PasswordController Integration Tests")
class PasswordControllerIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String NEW_PASSWORD = "NewSecurePass123!";

    @Autowired private MockMvcTester mvc;

    @MockitoBean private PasswordService passwordService;
    @MockitoBean private CurrentUserProvider currentUserProvider;

    private CurrentUser currentUser() {
        return new CurrentUser(
                USER_ID,
                "test@example.com",
                UserStatus.ACTIVE,
                Set.of("ROLE_USER"),
                CurrentUser.AuthenticationType.WEB_SESSION);
    }

    @Nested
    @DisplayName("POST /api/v1/users/me/password/set")
    class SetPassword {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 when valid request")
        void shouldReturn200_whenValidRequest() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());

            String requestBody =
                    """
                    {
                        "newPassword": "%s"
                    }
                    """
                            .formatted(NEW_PASSWORD);

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/users/me/password/set")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.success")
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("Should return 401 when no authentication")
        void shouldReturn401_whenNoAuthentication() {
            String requestBody =
                    """
                    {
                        "newPassword": "%s"
                    }
                    """
                            .formatted(NEW_PASSWORD);

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/users/me/password/set")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
                    .hasStatus(401);
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 409 when password already set")
        void shouldReturn409_whenPasswordAlreadySet() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.willThrow(new ConflictException(ErrorCode.USER_015))
                    .given(passwordService)
                    .setPassword(BDDMockito.eq(USER_ID), BDDMockito.any());

            String requestBody =
                    """
                    {
                        "newPassword": "%s"
                    }
                    """
                            .formatted(NEW_PASSWORD);

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/users/me/password/set")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
                    .hasStatus(409)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("USER_015");
        }
    }
}
