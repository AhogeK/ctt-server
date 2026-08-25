package com.ahogek.cttserver.sync.controller;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.filter.TermsCheckFilter;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.BaseControllerSliceTest;
import com.ahogek.cttserver.common.idempotent.IdempotentAspect;
import com.ahogek.cttserver.common.ratelimit.RateLimitAspect;
import com.ahogek.cttserver.sync.dto.SyncPullResponse;
import com.ahogek.cttserver.sync.dto.SyncPushResponse;
import com.ahogek.cttserver.sync.service.SyncPullService;
import com.ahogek.cttserver.sync.service.SyncPushService;
import com.ahogek.cttserver.user.enums.UserStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * {@link SyncController} MockMvc tests.
 *
 * <p>Covers the pull and push sync endpoints plus the authentication and validation boundaries the
 * controller is responsible for:
 *
 * <ul>
 *   <li>JWT bypass: JWT-authenticated users can access both endpoints
 *   <li>Authentication: unauthenticated requests receive 401
 *   <li>Validation: malformed request bodies receive 400
 * </ul>
 *
 * <p>Scope enforcement ({@code @RequiresApiKeyScope}) is tested in {@code
 * ApiKeyScopeIntegrationTest} because AOP aspects are not applied to controllers in
 * {@code @WebMvcTest} slice tests.
 */
@BaseControllerSliceTest(
        value = SyncController.class,
        excludeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                            RateLimitAspect.class,
                            IdempotentAspect.class,
                            TermsCheckFilter.class
                        }))
@DisplayName("SyncController MockMvc Tests")
class SyncControllerMockMvcTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID DEVICE_ID = UUID.fromString("3f2a1b4c-5d6e-4f7a-8b9c-0d1e2f3a4b5c");

    @Autowired private MockMvcTester mvc;

    @MockitoBean private SyncPullService syncPullService;
    @MockitoBean private SyncPushService syncPushService;
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
    @DisplayName("POST /api/v1/sync/pull")
    class PullTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 with changes when JWT user pulls")
        void shouldReturn200_whenJwtUserPulls() {
            when(currentUserProvider.getCurrentUserRequired()).thenReturn(currentUser());
            when(syncPullService.pull(any(), any(), anyLong()))
                    .thenReturn(new SyncPullResponse(List.of(), 42L));

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/sync/pull")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"deviceId": "%s", "lastPulledChangeId": 10}
                                            """
                                                    .formatted(DEVICE_ID))
                                    .exchange())
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.success")
                    .isEqualTo(true);
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/sync/pull")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"deviceId": "%s", "lastPulledChangeId": 10}
                                            """
                                                    .formatted(DEVICE_ID))
                                    .exchange())
                    .bodyJson()
                    .extractingPath("$.data.nextCursor")
                    .isEqualTo(42);
        }

        @Test
        @DisplayName("Should return 401 when no authentication is provided")
        void shouldReturn401_whenNotAuthenticated() {
            assertThat(mvc.post().uri("/api/v1/sync/pull").with(csrf()).exchange()).hasStatus(401);
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 when request body is invalid")
        void shouldReturn400_whenRequestBodyInvalid() {
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/sync/pull")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"lastPulledChangeId\": -1}")
                                    .exchange())
                    .hasStatus(400);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/sync/push")
    class PushTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 with next cursor when JWT user pushes")
        void shouldReturn200_whenJwtUserPushes() {
            when(currentUserProvider.getCurrentUserRequired()).thenReturn(currentUser());
            when(syncPushService.push(any(), any(), any())).thenReturn(new SyncPushResponse(7L));

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/sync/push")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {
                                              "deviceId": "%s",
                                              "sessions": [
                                                {
                                                  "sessionUuid": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
                                                  "projectName": "ctt-server",
                                                  "language": "Java",
                                                  "startTime": "2026-08-25T09:00:00Z",
                                                  "endTime": "2026-08-25T10:00:00Z",
                                                  "clientModifiedAt": "2026-08-25T10:00:00Z",
                                                  "clientVersion": 2,
                                                  "deleted": false
                                                }
                                              ]
                                            }
                                            """
                                                    .formatted(DEVICE_ID))
                                    .exchange())
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.success")
                    .isEqualTo(true);
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/sync/push")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {
                                              "deviceId": "%s",
                                              "sessions": [
                                                {
                                                  "sessionUuid": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
                                                  "projectName": "ctt-server",
                                                  "language": "Java",
                                                  "startTime": "2026-08-25T09:00:00Z",
                                                  "endTime": "2026-08-25T10:00:00Z",
                                                  "clientModifiedAt": "2026-08-25T10:00:00Z",
                                                  "clientVersion": 2,
                                                  "deleted": false
                                                }
                                              ]
                                            }
                                            """
                                                    .formatted(DEVICE_ID))
                                    .exchange())
                    .bodyJson()
                    .extractingPath("$.data.nextCursor")
                    .isEqualTo(7);
        }

        @Test
        @DisplayName("Should return 401 when no authentication is provided")
        void shouldReturn401_whenNotAuthenticated() {
            assertThat(mvc.post().uri("/api/v1/sync/push").with(csrf()).exchange()).hasStatus(401);
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 when sessions list is empty")
        void shouldReturn400_whenSessionsEmpty() {
            assertThat(
                            mvc.post()
                                    .uri("/api/v1/sync/push")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"deviceId": "%s", "sessions": []}
                                            """
                                                    .formatted(DEVICE_ID))
                                    .exchange())
                    .hasStatus(400);
        }
    }
}
