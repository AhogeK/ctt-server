package com.ahogek.cttserver.user.controller;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.filter.TermsCheckFilter;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.BaseControllerSliceTest;
import com.ahogek.cttserver.common.idempotent.IdempotentAspect;
import com.ahogek.cttserver.common.ratelimit.RateLimitAspect;
import com.ahogek.cttserver.user.dto.UserProfileResponse;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.service.UserProfileService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserController HTTP business logic tests.
 *
 * <p>Covers the GET /api/v1/users/me endpoint: full profile, email verification flag, missing
 * authentication, service delegation, and sensitive field exclusion. Verifies that sensitive fields
 * (passwordHash, lastLoginIp, version) never appear in the response payload.
 */
@BaseControllerSliceTest(
        value = UserController.class,
        excludeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                            RateLimitAspect.class,
                            IdempotentAspect.class,
                            TermsCheckFilter.class
                        }))
@DisplayName("UserController MockMvc Tests")
class UserControllerMockMvcTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Autowired private MockMvcTester mvc;

    @MockitoBean private UserProfileService userProfileService;
    @MockitoBean private CurrentUserProvider currentUserProvider;

    private CurrentUser currentUser() {
        return new CurrentUser(
                UserControllerMockMvcTest.USER_ID,
                "test@example.com",
                UserStatus.ACTIVE,
                Set.of("ROLE_USER"),
                CurrentUser.AuthenticationType.WEB_SESSION);
    }

    private UserProfileResponse fullProfile() {
        return new UserProfileResponse(
                USER_ID,
                "test@example.com",
                "Test User",
                true,
                Instant.parse("2026-01-15T10:30:00Z"),
                Instant.parse("2026-07-01T09:15:00Z"),
                "1.0.0");
    }

    private UserProfileResponse unverifiedProfile() {
        return new UserProfileResponse(
                USER_ID,
                "test@example.com",
                "Test User",
                false,
                Instant.parse("2026-01-15T10:30:00Z"),
                null,
                "1.0.0");
    }

    @Nested
    @DisplayName("GET /api/v1/users/me")
    class GetCurrentUserProfileTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 with all 7 profile fields when authenticated")
        void shouldReturn200_withFullProfile_whenAuthenticated() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(userProfileService.getCurrentUserProfile(USER_ID))
                    .willReturn(fullProfile());

            var result = mvc.get().uri("/api/v1/users/me").exchange();

            assertThat(result).hasStatusOk().bodyJson().extractingPath("$.success").isEqualTo(true);
            assertThat(result).bodyJson().extractingPath("$.data.id").isEqualTo(USER_ID.toString());
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.email")
                    .isEqualTo("test@example.com");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.displayName")
                    .isEqualTo("Test User");
            assertThat(result).bodyJson().extractingPath("$.data.emailVerified").isEqualTo(true);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.createdAt")
                    .isEqualTo("2026-01-15T10:30:00Z");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.lastLoginAt")
                    .isEqualTo("2026-07-01T09:15:00Z");
            assertThat(result).bodyJson().extractingPath("$.data.termsVersion").isEqualTo("1.0.0");
        }

        @Test
        @WithMockUser
        @DisplayName("Should return emailVerified false when user has not verified email")
        void shouldReturnEmailVerifiedFalse_whenNotVerified() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(userProfileService.getCurrentUserProfile(USER_ID))
                    .willReturn(unverifiedProfile());

            var result = mvc.get().uri("/api/v1/users/me").exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.data.emailVerified").isEqualTo(false);
        }

        @Test
        @DisplayName("Should return 401 when no authentication is provided")
        void shouldReturn401_whenNoAuthentication() {
            assertThat(mvc.get().uri("/api/v1/users/me").exchange()).hasStatus(401);
        }

        @Test
        @WithMockUser
        @DisplayName("Should pass authenticated user ID to service layer")
        void shouldPassAuthenticatedUserIdToService() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(userProfileService.getCurrentUserProfile(USER_ID))
                    .willReturn(fullProfile());

            mvc.get().uri("/api/v1/users/me").exchange();

            BDDMockito.then(userProfileService).should().getCurrentUserProfile(USER_ID);
        }

        @Test
        @WithMockUser
        @DisplayName("Should include lastLoginAt in response even when null (JsonInclude ALWAYS)")
        void shouldIncludeLastLoginAt_evenWhenNull() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(userProfileService.getCurrentUserProfile(USER_ID))
                    .willReturn(unverifiedProfile());

            assertThat(mvc.get().uri("/api/v1/users/me").exchange())
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.data.lastLoginAt")
                    .isNull();
        }

        @Test
        @WithMockUser
        @DisplayName("Should never expose passwordHash, lastLoginIp, or version in response")
        void shouldNeverExposeSensitiveFields_inResponse() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(userProfileService.getCurrentUserProfile(USER_ID))
                    .willReturn(fullProfile());

            assertThat(mvc.get().uri("/api/v1/users/me").exchange())
                    .hasStatusOk()
                    .bodyJson()
                    .doesNotHavePath("$.data.passwordHash")
                    .doesNotHavePath("$.data.lastLoginIp")
                    .doesNotHavePath("$.data.version");
        }
    }
}
