package com.ahogek.cttserver.sync.controller;

import com.ahogek.cttserver.auth.filter.TermsCheckFilter;
import com.ahogek.cttserver.common.BaseControllerSliceTest;
import com.ahogek.cttserver.common.idempotent.IdempotentAspect;
import com.ahogek.cttserver.common.ratelimit.RateLimitAspect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * {@link SyncController} MockMvc tests.
 *
 * <p>Covers the pull and push sync endpoints plus the authentication boundaries the controller is
 * responsible for:
 *
 * <ul>
 *   <li>JWT bypass: JWT-authenticated users can access both endpoints
 *   <li>Authentication: unauthenticated requests receive 401
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

    @Autowired private MockMvcTester mvc;

    @Nested
    @DisplayName("POST /api/v1/sync/pull")
    class PullTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 when JWT user accesses pull endpoint")
        void shouldReturn200_whenJwtUserAccessesPull() {
            assertThat(mvc.post().uri("/api/v1/sync/pull").with(csrf()).exchange())
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.success")
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("Should return 401 when no authentication is provided")
        void shouldReturn401_whenNotAuthenticated() {
            assertThat(mvc.post().uri("/api/v1/sync/pull").with(csrf()).exchange()).hasStatus(401);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/sync/push")
    class PushTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 when JWT user accesses push endpoint")
        void shouldReturn200_whenJwtUserAccessesPush() {
            assertThat(mvc.post().uri("/api/v1/sync/push").with(csrf()).exchange())
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.success")
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("Should return 401 when no authentication is provided")
        void shouldReturn401_whenNotAuthenticated() {
            assertThat(mvc.post().uri("/api/v1/sync/push").with(csrf()).exchange()).hasStatus(401);
        }
    }
}
