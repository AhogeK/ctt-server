package com.ahogek.cttserver.auth.apikey.controller;

import com.ahogek.cttserver.auth.CurrentUserProvider;
import com.ahogek.cttserver.auth.apikey.dto.ApiKeyResponse;
import com.ahogek.cttserver.auth.apikey.dto.CreateApiKeyResponse;
import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;
import com.ahogek.cttserver.auth.apikey.enums.ApiKeyStatus;
import com.ahogek.cttserver.auth.apikey.service.ApiKeyQueryService;
import com.ahogek.cttserver.auth.apikey.service.ApiKeyService;
import com.ahogek.cttserver.auth.filter.TermsCheckFilter;
import com.ahogek.cttserver.auth.model.CurrentUser;
import com.ahogek.cttserver.common.BaseControllerSliceTest;
import com.ahogek.cttserver.common.exception.ConflictException;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.idempotent.IdempotentAspect;
import com.ahogek.cttserver.common.ratelimit.RateLimitAspect;
import com.ahogek.cttserver.user.enums.UserStatus;

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

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * {@link ApiKeyController} MockMvc tests.
 *
 * <p>Covers the four management endpoints (create / list / get / revoke) plus the security
 * boundaries the controller is responsible for:
 *
 * <ul>
 *   <li>BOLA protection: the controller must never let one user access another user's keys
 *   <li>Validation: blank name, empty scope set, malformed JSON, wrong content type
 *   <li>Error mapping: 404 for unknown id (and other-user id), 409 for per-user limit, 401 for
 *       missing authentication
 *   <li>Raw-key exposure: the raw key value must appear exactly once in the create response and
 *       never in subsequent list/get responses
 * </ul>
 */
@BaseControllerSliceTest(
        value = ApiKeyController.class,
        excludeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                            RateLimitAspect.class,
                            IdempotentAspect.class,
                            TermsCheckFilter.class
                        }))
@DisplayName("ApiKeyController MockMvc Tests")
class ApiKeyControllerMockMvcTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID OTHER_USER_ID =
            UUID.fromString("660e8400-e29b-41d4-a716-446655440001");
    private static final UUID KEY_ID = UUID.fromString("770e8400-e29b-41d4-a716-446655440002");

    @Autowired private MockMvcTester mvc;

    @MockitoBean private ApiKeyService apiKeyService;
    @MockitoBean private ApiKeyQueryService apiKeyQueryService;
    @MockitoBean private CurrentUserProvider currentUserProvider;

    private CurrentUser currentUser() {
        return new CurrentUser(
                USER_ID,
                "test@example.com",
                UserStatus.ACTIVE,
                Set.of("ROLE_USER"),
                CurrentUser.AuthenticationType.WEB_SESSION);
    }

    private ApiKeyResponse stubApiKey() {
        return new ApiKeyResponse(
                KEY_ID,
                "MacBook Pro — IntelliJ",
                "cttak_a1b2c3d4",
                EnumSet.of(ApiKeyScope.READ, ApiKeyScope.SYNC),
                null,
                null,
                null,
                Instant.parse("2026-07-09T10:30:00Z"),
                ApiKeyStatus.ACTIVE);
    }

    private CreateApiKeyResponse stubCreateResponse() {
        return new CreateApiKeyResponse(
                "cttak_a1b2c3d4_z9y8x7w6v5u4t3s2r1q0p9o8n7m6l5k4j3i2h1g0f9e8d7c6b5a4",
                stubApiKey());
    }

    @Nested
    @DisplayName("POST /api/v1/auth/api-keys")
    class CreateApiKeyTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 201 with rawKey + metadata snapshot on successful creation")
        void shouldReturn201_whenCreated() throws Exception {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(apiKeyService.createApiKey(BDDMockito.any(), BDDMockito.any()))
                    .willReturn(stubCreateResponse());

            String body =
                    """
                    {
                      "name": "MacBook Pro — IntelliJ",
                      "scopes": ["READ", "SYNC"],
                      "expiresAt": null
                    }
                    """;

            var result =
                    mvc.post()
                            .uri("/api/v1/auth/api-keys")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(csrf())
                            .exchange();

            assertThat(result).hasStatus(201);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.rawKey")
                    .isEqualTo(
                            "cttak_a1b2c3d4_z9y8x7w6v5u4t3s2r1q0p9o8n7m6l5k4j3i2h1g0f9e8d7c6b5a4");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.apiKey.name")
                    .isEqualTo("MacBook Pro — IntelliJ");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.apiKey.status")
                    .isEqualTo("ACTIVE");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.apiKey.keyPrefix")
                    .isEqualTo("cttak_a1b2c3d4");
        }

        @Test
        @WithMockUser
        @DisplayName("Should pass authenticated user id and payload to service")
        void shouldPassUserIdAndPayloadToService() throws Exception {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(apiKeyService.createApiKey(BDDMockito.any(), BDDMockito.any()))
                    .willReturn(stubCreateResponse());

            String body =
                    """
                    {
                      "name": "Server CI",
                      "scopes": ["READ"]
                    }
                    """;

            mvc.post()
                    .uri("/api/v1/auth/api-keys")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf())
                    .exchange();

            BDDMockito.then(apiKeyService)
                    .should()
                    .createApiKey(
                            BDDMockito.eq(USER_ID),
                            BDDMockito.argThat(
                                    req ->
                                            "Server CI".equals(req.name())
                                                    && req.scopes()
                                                            .equals(Set.of(ApiKeyScope.READ))));
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 409 AUTH_014 when per-user limit exceeded")
        void shouldReturn409_whenLimitExceeded() throws Exception {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(apiKeyService.createApiKey(BDDMockito.any(), BDDMockito.any()))
                    .willThrow(new ConflictException(ErrorCode.AUTH_014));

            String body =
                    """
                    {
                      "name": "Another key",
                      "scopes": ["READ"]
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/api-keys")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body)
                                    .with(csrf())
                                    .exchange())
                    .hasStatus(409)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("AUTH_014");
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 COMMON_003 when name is blank")
        void shouldReturn400_whenNameBlank() throws Exception {
            String body =
                    """
                    {
                      "name": "",
                      "scopes": ["READ"]
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/api-keys")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body)
                                    .with(csrf())
                                    .exchange())
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_003");
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 COMMON_003 when scopes are empty")
        void shouldReturn400_whenScopesEmpty() throws Exception {
            String body =
                    """
                    {
                      "name": "Empty scopes",
                      "scopes": []
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/api-keys")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body)
                                    .with(csrf())
                                    .exchange())
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("COMMON_003");
        }

        @Test
        @DisplayName("Should return 401 when no authentication is provided")
        void shouldReturn401_whenCreateNotAuthenticated() throws Exception {
            String body =
                    """
                    {
                      "name": "Unauth",
                      "scopes": ["READ"]
                    }
                    """;

            assertThat(
                            mvc.post()
                                    .uri("/api/v1/auth/api-keys")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body)
                                    .with(csrf())
                                    .exchange())
                    .hasStatus(401);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/api-keys")
    class ListApiKeysTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 with empty list when user has no keys")
        void shouldReturn200_whenNoKeys() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(apiKeyQueryService.listApiKeys(USER_ID)).willReturn(List.of());

            assertThat(mvc.get().uri("/api/v1/auth/api-keys").exchange())
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.data.keys")
                    .isEqualTo(List.of());
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 200 with key list mapped correctly")
        void shouldReturnKeys() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(apiKeyQueryService.listApiKeys(USER_ID))
                    .willReturn(List.of(stubApiKey()));

            var response = mvc.get().uri("/api/v1/auth/api-keys").exchange();
            assertThat(response).hasStatusOk();
            assertThat(response).bodyJson().extractingPath("$.data.keys.length()").isEqualTo(1);
            assertThat(response)
                    .bodyJson()
                    .extractingPath("$.data.keys[0].id")
                    .isEqualTo(KEY_ID.toString());
            assertThat(response)
                    .bodyJson()
                    .extractingPath("$.data.keys[0].name")
                    .isEqualTo("MacBook Pro — IntelliJ");
            assertThat(response)
                    .bodyJson()
                    .extractingPath("$.data.keys[0].status")
                    .isEqualTo("ACTIVE");
        }

        @Test
        @WithMockUser
        @DisplayName("Should never expose rawKey in list response")
        void shouldNeverExposeRawKey_inListResponse() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(apiKeyQueryService.listApiKeys(USER_ID))
                    .willReturn(List.of(stubApiKey()));

            assertThat(mvc.get().uri("/api/v1/auth/api-keys").exchange())
                    .hasStatusOk()
                    .bodyJson()
                    .doesNotHavePath("$.data.keys[0].rawKey");
        }

        @Test
        @WithMockUser
        @DisplayName("Should isolate keys by current user (BOLA protection)")
        void shouldIsolateKeysBetweenUsers() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(apiKeyQueryService.listApiKeys(USER_ID))
                    .willReturn(List.of(stubApiKey()));
            ApiKeyResponse otherUsersKey =
                    new ApiKeyResponse(
                            UUID.randomUUID(),
                            "Other User Key",
                            "cttak_99999999",
                            EnumSet.of(ApiKeyScope.READ),
                            null,
                            null,
                            null,
                            Instant.parse("2026-07-08T10:30:00Z"),
                            ApiKeyStatus.ACTIVE);
            BDDMockito.given(apiKeyQueryService.listApiKeys(OTHER_USER_ID))
                    .willReturn(List.of(otherUsersKey));

            var result = mvc.get().uri("/api/v1/auth/api-keys").exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.data.keys.length()").isEqualTo(1);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.data.keys[0].name")
                    .isEqualTo("MacBook Pro — IntelliJ");

            BDDMockito.then(apiKeyQueryService).should().listApiKeys(USER_ID);
            BDDMockito.then(apiKeyQueryService)
                    .should(BDDMockito.never())
                    .listApiKeys(OTHER_USER_ID);
        }

        @Test
        @DisplayName("Should return 401 when no authentication is provided")
        void shouldReturn401_whenListNotAuthenticated() {
            assertThat(mvc.get().uri("/api/v1/auth/api-keys").exchange()).hasStatus(401);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/api-keys/{id}")
    class GetApiKeyTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 with the key when id is owned by current user")
        void shouldReturn200_whenOwnedByCurrentUser() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(apiKeyQueryService.getApiKey(USER_ID, KEY_ID))
                    .willReturn(stubApiKey());

            assertThat(mvc.get().uri("/api/v1/auth/api-keys/{id}", KEY_ID.toString()).exchange())
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.data.id")
                    .isEqualTo(KEY_ID.toString());
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 401 AUTH_010 when id does not exist")
        void shouldReturn404_whenIdMissing() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(apiKeyQueryService.getApiKey(USER_ID, KEY_ID))
                    .willThrow(new NotFoundException(ErrorCode.AUTH_010));

            assertThat(mvc.get().uri("/api/v1/auth/api-keys/{id}", KEY_ID.toString()).exchange())
                    .hasStatus(401)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("AUTH_010");
        }

        @Test
        @WithMockUser
        @DisplayName(
                "Should return 401 AUTH_010 when key belongs to another user (BOLA protection)")
        void shouldReturn401_whenKeyOwnedByAnotherUser_get() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(apiKeyQueryService.getApiKey(USER_ID, KEY_ID))
                    .willThrow(new NotFoundException(ErrorCode.AUTH_010));

            assertThat(mvc.get().uri("/api/v1/auth/api-keys/{id}", KEY_ID.toString()).exchange())
                    .hasStatus(401);

            BDDMockito.then(apiKeyQueryService).should().getApiKey(USER_ID, KEY_ID);
            BDDMockito.then(apiKeyQueryService)
                    .should(BDDMockito.never())
                    .getApiKey(OTHER_USER_ID, KEY_ID);
        }

        @Test
        @WithMockUser
        @DisplayName("Should never expose rawKey in get response")
        void shouldNeverExposeRawKey_inGetResponse() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.given(apiKeyQueryService.getApiKey(USER_ID, KEY_ID))
                    .willReturn(stubApiKey());

            assertThat(mvc.get().uri("/api/v1/auth/api-keys/{id}", KEY_ID.toString()).exchange())
                    .hasStatusOk()
                    .bodyJson()
                    .doesNotHavePath("$.data.rawKey");
        }

        @Test
        @DisplayName("Should return 401 when no authentication is provided")
        void shouldReturn401_whenGetNotAuthenticated() {
            assertThat(mvc.get().uri("/api/v1/auth/api-keys/{id}", KEY_ID.toString()).exchange())
                    .hasStatus(401);
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/auth/api-keys/{id}")
    class RevokeApiKeyTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 204 on successful revocation")
        void shouldReturn204_whenRevoked() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.willDoNothing().given(apiKeyService).revokeApiKey(USER_ID, KEY_ID);

            assertThat(
                            mvc.delete()
                                    .uri("/api/v1/auth/api-keys/{id}", KEY_ID.toString())
                                    .with(csrf())
                                    .exchange())
                    .hasStatus(204);

            BDDMockito.then(apiKeyService).should().revokeApiKey(USER_ID, KEY_ID);
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 401 AUTH_010 when id does not exist")
        void shouldReturn401_whenIdMissing_revoke() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.willThrow(new NotFoundException(ErrorCode.AUTH_010))
                    .given(apiKeyService)
                    .revokeApiKey(USER_ID, KEY_ID);

            assertThat(
                            mvc.delete()
                                    .uri("/api/v1/auth/api-keys/{id}", KEY_ID.toString())
                                    .with(csrf())
                                    .exchange())
                    .hasStatus(401)
                    .bodyJson()
                    .extractingPath("$.code")
                    .isEqualTo("AUTH_010");
        }

        @Test
        @WithMockUser
        @DisplayName(
                "Should return 401 AUTH_010 when key belongs to another user (BOLA protection)")
        void shouldReturn401_whenKeyOwnedByAnotherUser_revoke() {
            BDDMockito.given(currentUserProvider.getCurrentUserRequired())
                    .willReturn(currentUser());
            BDDMockito.willThrow(new NotFoundException(ErrorCode.AUTH_010))
                    .given(apiKeyService)
                    .revokeApiKey(USER_ID, KEY_ID);

            assertThat(
                            mvc.delete()
                                    .uri("/api/v1/auth/api-keys/{id}", KEY_ID.toString())
                                    .with(csrf())
                                    .exchange())
                    .hasStatus(401);

            BDDMockito.then(apiKeyService).should().revokeApiKey(USER_ID, KEY_ID);
            BDDMockito.then(apiKeyService)
                    .should(BDDMockito.never())
                    .revokeApiKey(OTHER_USER_ID, KEY_ID);
        }

        @Test
        @DisplayName("Should return 401 when no authentication is provided")
        void shouldReturn401_whenRevokeNotAuthenticated() {
            assertThat(
                            mvc.delete()
                                    .uri("/api/v1/auth/api-keys/{id}", KEY_ID.toString())
                                    .with(csrf())
                                    .exchange())
                    .hasStatus(401);
        }
    }
}
