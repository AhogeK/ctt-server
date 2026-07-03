package com.ahogek.cttserver.user;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.filter.TermsCheckFilter;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.BaseControllerSliceTest;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.common.idempotent.IdempotentAspect;
import com.ahogek.cttserver.common.ratelimit.RateLimitAspect;
import com.ahogek.cttserver.common.response.EmptyResponse;
import com.ahogek.cttserver.user.controller.EmailChangeController;
import com.ahogek.cttserver.user.dto.EmailStatusResponse;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.service.EmailChangeService;

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
 * EmailChangeController integration tests.
 *
 * <p>Covers the four email change endpoints: change-request, change-confirm, cancel, and status.
 * Uses MockMvc for HTTP testing with mocked service dependencies.
 */
@BaseControllerSliceTest(
        value = EmailChangeController.class,
        excludeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                            RateLimitAspect.class,
                            IdempotentAspect.class,
                            TermsCheckFilter.class
                        }))
@DisplayName("EmailChangeController Integration Tests")
class EmailChangeIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String NEW_EMAIL = "new@example.com";
    private static final String CURRENT_PASSWORD = "CurrentPass123!";

    @Autowired private MockMvcTester mvc;

    @MockitoBean private EmailChangeService emailChangeService;
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
    @DisplayName("POST /api/v1/users/me/email/change-request")
    class RequestEmailChangeTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 and send verification email when valid request")
        void shouldReturn200AndSendVerificationEmail_whenValidRequest() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(
                            emailChangeService.requestEmailChange(
                                    BDDMockito.eq(USER_ID),
                                    BDDMockito.eq(NEW_EMAIL),
                                    BDDMockito.eq(CURRENT_PASSWORD),
                                    BDDMockito.any(),
                                    BDDMockito.any()))
                    .willReturn(EmptyResponse.ok());

            String requestBody =
                    """
                    {
                        "newEmail": "%s",
                        "password": "%s"
                    }
                    """
                            .formatted(NEW_EMAIL, CURRENT_PASSWORD);

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/users/me/email/change-request")
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
        @DisplayName("Should return 409 when email already registered")
        void shouldReturn409_whenEmailAlreadyRegistered() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(
                            emailChangeService.requestEmailChange(
                                    BDDMockito.eq(USER_ID),
                                    BDDMockito.eq(NEW_EMAIL),
                                    BDDMockito.eq(CURRENT_PASSWORD),
                                    BDDMockito.any(),
                                    BDDMockito.any()))
                    .willThrow(new ConflictException(ErrorCode.USER_001));

            String requestBody =
                    """
                    {
                        "newEmail": "%s",
                        "password": "%s"
                    }
                    """
                            .formatted(NEW_EMAIL, CURRENT_PASSWORD);

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/users/me/email/change-request")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
                    .hasStatus(409)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("USER_001");
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 401 when password incorrect")
        void shouldReturn401_whenPasswordIncorrect() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(
                            emailChangeService.requestEmailChange(
                                    BDDMockito.eq(USER_ID),
                                    BDDMockito.eq(NEW_EMAIL),
                                    BDDMockito.eq(CURRENT_PASSWORD),
                                    BDDMockito.any(),
                                    BDDMockito.any()))
                    .willThrow(new UnauthorizedException(ErrorCode.USER_014));

            String requestBody =
                    """
                    {
                        "newEmail": "%s",
                        "password": "%s"
                    }
                    """
                            .formatted(NEW_EMAIL, CURRENT_PASSWORD);

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/users/me/email/change-request")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
                    .hasStatus(401)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("USER_014");
        }

        @Test
        @DisplayName("Should return 401 when no authentication provided")
        void shouldReturn401_whenNoAuthentication() {
            String requestBody =
                    """
                    {
                        "newEmail": "%s",
                        "password": "%s"
                    }
                    """
                            .formatted(NEW_EMAIL, CURRENT_PASSWORD);

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/users/me/email/change-request")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
                    .hasStatus(401);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/users/me/email/change-confirm")
    class ConfirmEmailChangeTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 and change email when valid token")
        void shouldReturn200AndChangeEmail_whenValidToken() {
            BDDMockito.doNothing()
                    .when(emailChangeService)
                    .confirmEmailChange(
                            BDDMockito.anyString(), BDDMockito.anyString(), BDDMockito.anyString());

            String requestBody =
                    """
                    {
                        "token": "valid-token-123"
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/users/me/email/change-confirm")
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
        @DisplayName("Should return 400 when token invalid")
        void shouldReturn400_whenTokenInvalid() {
            BDDMockito.doThrow(new UnauthorizedException(ErrorCode.USER_011))
                    .when(emailChangeService)
                    .confirmEmailChange(BDDMockito.any(), BDDMockito.any(), BDDMockito.any());

            String requestBody =
                    """
                    {
                        "token": "invalid-token"
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/users/me/email/change-confirm")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("USER_011");
        }

        @Test
        @DisplayName("Should return 401 when no authentication provided")
        void shouldReturn401_whenNoAuthentication() {
            String requestBody =
                    """
                    {
                        "token": "some-token"
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/users/me/email/change-confirm")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
                    .hasStatus(401);
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 when token is blank")
        void shouldReturn400_whenTokenIsBlank() {
            String requestBody =
                    """
                    {
                        "token": ""
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/users/me/email/change-confirm")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_003");
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/users/me/email/change-request")
    class CancelEmailChangeTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 and cancel pending change")
        void shouldReturn200AndCancelPendingChange() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.doNothing().when(emailChangeService).cancelEmailChange(USER_ID);

            assertThat(mvc.delete().uri("/api/v1/users/me/email/change-request").with(csrf()))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.success")
                    .isEqualTo(true);

            BDDMockito.then(emailChangeService).should().cancelEmailChange(USER_ID);
        }

        @Test
        @DisplayName("Should return 401 when no authentication provided")
        void shouldReturn401_whenNoAuthentication() {
            assertThat(mvc.delete().uri("/api/v1/users/me/email/change-request").with(csrf()))
                    .hasStatus(401);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/me/email/status")
    class GetEmailStatusTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 with email status")
        void shouldReturn200WithEmailStatus() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            EmailStatusResponse statusResponse =
                    new EmailStatusResponse("test@example.com", true, false, null);
            BDDMockito.given(emailChangeService.getEmailStatus(USER_ID)).willReturn(statusResponse);

            var result = mvc.get().uri("/api/v1/users/me/email/status").exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.success").isEqualTo(true);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.email")
                    .isEqualTo("test@example.com");
            assertThat(result).bodyJson().extractingPath("$.data.emailVerified").isEqualTo(true);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.emailChangePending")
                    .isEqualTo(false);
            assertThat(result).bodyJson().extractingPath("$.data.pendingNewEmail").isNull();
        }

        @Test
        @WithMockUser
        @DisplayName("Should return pending new email when change is pending")
        void shouldReturnPendingNewEmail_whenChangeIsPending() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            EmailStatusResponse statusResponse =
                    new EmailStatusResponse("test@example.com", true, true, NEW_EMAIL);
            BDDMockito.given(emailChangeService.getEmailStatus(USER_ID)).willReturn(statusResponse);

            var result = mvc.get().uri("/api/v1/users/me/email/status").exchange();

            assertThat(result).hasStatusOk();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.emailChangePending")
                    .isEqualTo(true);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.pendingNewEmail")
                    .isEqualTo(NEW_EMAIL);
        }

        @Test
        @DisplayName("Should return 401 when no authentication provided")
        void shouldReturn401_whenNoAuthentication() {
            assertThat(mvc.get().uri("/api/v1/users/me/email/status")).hasStatus(401);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/users/me/email/resend-verification")
    class ResendVerification {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 when pending token exists")
        void shouldReturn200_whenPendingTokenExists() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(
                            emailChangeService.resendEmailChangeVerification(
                                    BDDMockito.eq(USER_ID), BDDMockito.any(), BDDMockito.any()))
                    .willReturn(EmptyResponse.ok());

            assertThat(mvc.post().uri("/api/v1/users/me/email/resend-verification").with(csrf()))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.success")
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("Should return 401 when no authentication provided")
        void shouldReturn401_whenNoAuthentication() {
            assertThat(mvc.post().uri("/api/v1/users/me/email/resend-verification").with(csrf()))
                    .hasStatus(401);
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 409 when no pending token")
        void shouldReturn409_whenNoPendingToken() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(
                            emailChangeService.resendEmailChangeVerification(
                                    BDDMockito.eq(USER_ID), BDDMockito.any(), BDDMockito.any()))
                    .willThrow(new ConflictException(ErrorCode.USER_009));

            assertThat(mvc.post().uri("/api/v1/users/me/email/resend-verification").with(csrf()))
                    .hasStatus(409)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("USER_009");
        }
    }
}
