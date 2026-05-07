package com.ahogek.cttserver.auth.filter;

import com.ahogek.cttserver.auth.infrastructure.security.PublicApiEndpointRegistry;
import com.ahogek.cttserver.common.config.properties.TermsProperties;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TermsCheckFilter.
 *
 * <p>Tests the filter logic for checking user's accepted terms version against the current active
 * version.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-05-05
 */
@DisplayName("TermsCheckFilter Tests")
class TermsCheckFilterTest {

    private TermsCheckFilter filter;
    private TermsProperties termsProperties;
    private ObjectMapper objectMapper;
    private JwtDecoder jwtDecoder;
    private PublicApiEndpointRegistry publicApiEndpointRegistry;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() throws IOException {
        termsProperties = mock(TermsProperties.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        jwtDecoder = mock(JwtDecoder.class);
        publicApiEndpointRegistry = mock(PublicApiEndpointRegistry.class);
        filter =
                new TermsCheckFilter(
                        termsProperties, objectMapper, jwtDecoder, publicApiEndpointRegistry);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        outputStream = new ByteArrayOutputStream();

        when(response.getOutputStream()).thenReturn(new MockServletOutputStream(outputStream));
        when(termsProperties.termsAcceptPath()).thenReturn("/api/v1/auth/terms/accept");
        when(publicApiEndpointRegistry.getPublicUrlSet()).thenReturn(Set.of("/api/v1/auth/login"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should pass when terms version matches current version")
    void shouldPass_whenTermsVersionMatches() throws Exception {
        // Given
        String currentVersion = "1.0.0";
        String tokenValue = "valid.jwt.token";
        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        "user", tokenValue, Collections.emptyList());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        when(termsProperties.currentVersion()).thenReturn(currentVersion);
        when(request.getServletPath()).thenReturn("/api/v1/protected/resource");

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("termsVersion")).thenReturn(currentVersion);
        when(jwtDecoder.decode(tokenValue)).thenReturn(jwt);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(any(Integer.class));
    }

    @Test
    @DisplayName("Should pass when terms version is missing (pre-terms compatibility)")
    void shouldPass_whenTermsVersionMissing() throws Exception {
        // Given
        String currentVersion = "1.0.0";
        String tokenValue = "valid.jwt.token";
        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        "user", tokenValue, Collections.emptyList());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        when(termsProperties.currentVersion()).thenReturn(currentVersion);
        when(request.getServletPath()).thenReturn("/api/v1/protected/resource");

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("termsVersion")).thenReturn(null);
        when(jwtDecoder.decode(tokenValue)).thenReturn(jwt);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(any(Integer.class));
    }

    @Test
    @DisplayName("Should pass when terms version is blank (pre-terms compatibility)")
    void shouldPass_whenTermsVersionBlank() throws Exception {
        // Given
        String currentVersion = "1.0.0";
        String tokenValue = "valid.jwt.token";
        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        "user", tokenValue, Collections.emptyList());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        when(termsProperties.currentVersion()).thenReturn(currentVersion);
        when(request.getServletPath()).thenReturn("/api/v1/protected/resource");

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("termsVersion")).thenReturn("");
        when(jwtDecoder.decode(tokenValue)).thenReturn(jwt);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(any(Integer.class));
    }

    @Test
    @DisplayName("Should block with 403 AUTH_019 when terms version is outdated")
    void shouldBlock_whenTermsVersionOutdated() throws Exception {
        // Given
        String currentVersion = "1.0.0";
        String oldVersion = "0.9.0";
        String tokenValue = "valid.jwt.token";
        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        "user", tokenValue, Collections.emptyList());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        when(termsProperties.currentVersion()).thenReturn(currentVersion);
        when(request.getServletPath()).thenReturn("/api/v1/protected/resource");

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("termsVersion")).thenReturn(oldVersion);
        when(jwtDecoder.decode(tokenValue)).thenReturn(jwt);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(request, response);

        String jsonResponse = outputStream.toString();
        RestApiResponse<ErrorResponse> apiResponse =
                objectMapper.readValue(
                        jsonResponse,
                        objectMapper
                                .getTypeFactory()
                                .constructParametricType(
                                        RestApiResponse.class, ErrorResponse.class));

        assertThat(apiResponse.success()).isFalse();
        assertThat(apiResponse.data().code()).isEqualTo(ErrorCode.AUTH_019.name());
        assertThat(apiResponse.data().httpStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Should pass when no authentication present")
    void shouldPass_whenNoAuthentication() throws Exception {
        // Given
        SecurityContextHolder.clearContext();
        when(termsProperties.currentVersion()).thenReturn("1.0.0");
        when(request.getServletPath()).thenReturn("/api/v1/protected/resource");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(any(Integer.class));
    }

    @Test
    @DisplayName("Should pass when current version is null")
    void shouldPass_whenCurrentVersionNull() throws Exception {
        // Given
        String tokenValue = "valid.jwt.token";
        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        "user", tokenValue, Collections.emptyList());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        when(termsProperties.currentVersion()).thenReturn(null);
        when(request.getServletPath()).thenReturn("/api/v1/protected/resource");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(jwtDecoder, never()).decode(any(String.class));
    }

    @Test
    @DisplayName("Should pass when current version is blank")
    void shouldPass_whenCurrentVersionBlank() throws Exception {
        // Given
        String tokenValue = "valid.jwt.token";
        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        "user", tokenValue, Collections.emptyList());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        when(termsProperties.currentVersion()).thenReturn("");
        when(request.getServletPath()).thenReturn("/api/v1/protected/resource");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(jwtDecoder, never()).decode(any(String.class));
    }

    @Test
    @DisplayName("Should pass when credentials is not a string token")
    void shouldPass_whenCredentialsNotStringToken() throws Exception {
        // Given
        Authentication auth =
                new UsernamePasswordAuthenticationToken("user", null, Collections.emptyList());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        when(termsProperties.currentVersion()).thenReturn("1.0.0");
        when(request.getServletPath()).thenReturn("/api/v1/protected/resource");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(jwtDecoder, never()).decode(any(String.class));
    }

    @Test
    @DisplayName("Should pass when JWT decode fails")
    void shouldPass_whenJwtDecodeFails() throws Exception {
        // Given
        String currentVersion = "1.0.0";
        String tokenValue = "invalid.jwt.token";
        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        "user", tokenValue, Collections.emptyList());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        when(termsProperties.currentVersion()).thenReturn(currentVersion);
        when(request.getServletPath()).thenReturn("/api/v1/protected/resource");
        when(jwtDecoder.decode(tokenValue)).thenThrow(new RuntimeException("Invalid token"));

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(any(Integer.class));
    }

    @Test
    @DisplayName("Should skip filter for terms accept endpoint")
    void shouldSkip_whenTermsAcceptEndpoint() throws Exception {
        // Given
        when(request.getServletPath()).thenReturn("/api/v1/auth/terms/accept");

        // When
        boolean shouldNotFilter = filter.shouldNotFilter(request);

        // Then
        assertThat(shouldNotFilter).isTrue();
    }

    @Test
    @DisplayName("Should skip filter for error endpoint")
    void shouldSkip_whenErrorEndpoint() throws Exception {
        // Given
        when(request.getServletPath()).thenReturn("/error");

        // When
        boolean shouldNotFilter = filter.shouldNotFilter(request);

        // Then
        assertThat(shouldNotFilter).isTrue();
    }

    @Test
    @DisplayName("Should skip filter for actuator endpoint")
    void shouldSkip_whenActuatorEndpoint() throws Exception {
        // Given
        when(request.getServletPath()).thenReturn("/actuator/health");

        // When
        boolean shouldNotFilter = filter.shouldNotFilter(request);

        // Then
        assertThat(shouldNotFilter).isTrue();
    }

    @Test
    @DisplayName("Should skip filter for swagger-ui endpoint")
    void shouldSkip_whenSwaggerUiEndpoint() throws Exception {
        // Given
        when(request.getServletPath()).thenReturn("/swagger-ui/index.html");

        // When
        boolean shouldNotFilter = filter.shouldNotFilter(request);

        // Then
        assertThat(shouldNotFilter).isTrue();
    }

    @Test
    @DisplayName("Should skip filter for api-docs endpoint")
    void shouldSkip_whenApiDocsEndpoint() throws Exception {
        // Given
        when(request.getServletPath()).thenReturn("/v3/api-docs/swagger-config");

        // When
        boolean shouldNotFilter = filter.shouldNotFilter(request);

        // Then
        assertThat(shouldNotFilter).isTrue();
    }

    @Test
    @DisplayName("Should skip filter for public endpoint")
    void shouldSkip_whenPublicEndpoint() throws Exception {
        // Given
        when(request.getServletPath()).thenReturn("/api/v1/auth/login");

        // When
        boolean shouldNotFilter = filter.shouldNotFilter(request);

        // Then
        assertThat(shouldNotFilter).isTrue();
    }

    @Test
    @DisplayName("Should not skip filter for protected endpoint")
    void shouldNotSkip_whenProtectedEndpoint() throws Exception {
        // Given
        when(request.getServletPath()).thenReturn("/api/v1/protected/resource");

        // When
        boolean shouldNotFilter = filter.shouldNotFilter(request);

        // Then
        assertThat(shouldNotFilter).isFalse();
    }

    /**
     * Mock ServletOutputStream for testing purposes.
     *
     * <p>Wraps a ByteArrayOutputStream to capture response output.
     */
    private static class MockServletOutputStream extends ServletOutputStream {

        private final ByteArrayOutputStream outputStream;

        MockServletOutputStream(ByteArrayOutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        public void write(int b) {
            outputStream.write(b);
        }

        @Override
        public void write(byte @NonNull [] b) throws IOException {
            outputStream.write(b);
        }

        @Override
        public void write(byte @NonNull [] b, int off, int len) {
            outputStream.write(b, off, len);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {}
    }
}
