package com.ahogek.cttserver.auth.apikey.client;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.apikey.entity.ApiKey;
import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;
import com.ahogek.cttserver.auth.apikey.model.ApiKeyPrincipal;
import com.ahogek.cttserver.auth.apikey.service.ApiKeyService;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.user.entity.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.EnumSet;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyAuthenticationFilter Tests")
class ApiKeyAuthenticationFilterTest {

    private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID KEY_ID = UUID.fromString("770e8400-e29b-41d4-a716-446655440002");
    private static final String RAW_KEY =
            "cttak_a1b2c3d4_z9y8x7w6v5u4t3s2r1q0p9o8n7m6l5k4j3i2h1g0f9e8d7c6b5a4";

    @Mock private ApiKeyService apiKeyService;
    @Mock private AuditLogService auditLogService;

    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityProperties properties =
                new SecurityProperties(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new SecurityProperties.ApiKeyProperties("Authorization", "Bearer", 20));
        filter =
                new ApiKeyAuthenticationFilter(
                        apiKeyService,
                        auditLogService,
                        properties,
                        new ObjectMapper().registerModule(new JavaTimeModule()));
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("shouldPassThrough_whenNoAuthHeader")
    void shouldPassThrough_whenNoAuthHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("shouldPassThrough_whenBearerTokenIsJwt")
    void shouldPassThrough_whenBearerTokenIsJwt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(
                "Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("shouldAuthenticate_whenValidApiKey")
    void shouldAuthenticate_whenValidApiKey() throws Exception {
        User user = new User();
        user.setId(USER_ID);
        ApiKey apiKey = new ApiKey();
        apiKey.setId(KEY_ID);
        apiKey.setUser(user);
        apiKey.setScopes(EnumSet.of(ApiKeyScope.READ, ApiKeyScope.SYNC));

        given(apiKeyService.validateAndTouch(RAW_KEY)).willReturn(apiKey);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + RAW_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isInstanceOf(ApiKeyPrincipal.class);
        ApiKeyPrincipal principal =
                (ApiKeyPrincipal)
                        SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.userId()).isEqualTo(USER_ID);
        assertThat(principal.keyId()).isEqualTo(KEY_ID);
        assertThat(principal.scopes())
                .containsExactlyInAnyOrder(ApiKeyScope.READ, ApiKeyScope.SYNC);
        then(auditLogService)
                .should()
                .logSuccess(
                        USER_ID, AuditAction.API_KEY_USED, ResourceType.API_KEY, KEY_ID.toString());
    }

    @Test
    @DisplayName("shouldReturn403_whenUserAccountNotActive")
    void shouldReturn403_whenUserAccountNotActive() throws Exception {
        given(apiKeyService.validateAndTouch(RAW_KEY))
                .willThrow(new ForbiddenException(ErrorCode.AUTH_005));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + RAW_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("shouldReturn401_whenKeyNotFound")
    void shouldReturn401_whenKeyNotFound() throws Exception {
        given(apiKeyService.validateAndTouch(RAW_KEY))
                .willThrow(new NotFoundException(ErrorCode.AUTH_010));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + RAW_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        then(auditLogService)
                .should()
                .logFailure(
                        null,
                        AuditAction.API_KEY_AUTH_FAILED,
                        ResourceType.API_KEY,
                        null,
                        ErrorCode.AUTH_010.name());
    }

    @Test
    @DisplayName("shouldReturn401_whenKeyExpired")
    void shouldReturn401_whenKeyExpired() throws Exception {
        given(apiKeyService.validateAndTouch(RAW_KEY))
                .willThrow(new UnauthorizedException(ErrorCode.AUTH_011));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + RAW_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("shouldReturn403_whenKeyRevoked")
    void shouldReturn403_whenKeyRevoked() throws Exception {
        given(apiKeyService.validateAndTouch(RAW_KEY))
                .willThrow(new ForbiddenException(ErrorCode.AUTH_012));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + RAW_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("shouldReturn401_whenKeyPrefixIsEmpty")
    void shouldReturn401_whenKeyPrefixIsEmpty() throws Exception {
        given(apiKeyService.validateAndTouch("cttak_"))
                .willThrow(new NotFoundException(ErrorCode.AUTH_010));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer cttak_");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }
}
