package com.ahogek.cttserver.auth.filter;

import com.ahogek.cttserver.auth.infrastructure.security.PublicApiEndpointRegistry;
import com.ahogek.cttserver.common.config.properties.TermsProperties;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * Filter that checks if the user's accepted terms version matches the current active version.
 *
 * <p>Runs after JWT authentication (Spring Security's oauth2ResourceServer filter) and before
 * authorization. If the user's {@code termsVersion} claim in the JWT does not match the configured
 * {@code ctt.terms.currentVersion}, the request is rejected with HTTP 403 and AUTH_019 error.
 *
 * @author AhogeK
 * @since 2026-05-03
 */
@Component
public class TermsCheckFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TermsCheckFilter.class);

    private static final Set<String> SKIP_PREFIXES =
            Set.of("/error", "/actuator/", "/swagger-ui/", "/v3/api-docs/");

    private final TermsProperties termsProperties;
    private final ObjectMapper objectMapper;
    private final JwtDecoder jwtDecoder;
    private final PublicApiEndpointRegistry publicApiEndpointRegistry;

    public TermsCheckFilter(
            TermsProperties termsProperties,
            ObjectMapper objectMapper,
            JwtDecoder jwtDecoder,
            PublicApiEndpointRegistry publicApiEndpointRegistry) {
        this.termsProperties = termsProperties;
        this.objectMapper = objectMapper;
        this.jwtDecoder = jwtDecoder;
        this.publicApiEndpointRegistry = publicApiEndpointRegistry;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }

        String currentVersion = termsProperties.currentVersion();
        if (currentVersion == null || currentVersion.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        Object credentials = authentication.getCredentials();
        if (!(credentials instanceof String tokenValue)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Jwt jwt = jwtDecoder.decode(tokenValue);
            @Nullable String termsVersion = jwt.getClaimAsString("termsVersion");

            if (termsVersion != null
                    && !termsVersion.isBlank()
                    && !termsVersion.equals(currentVersion)) {
                log.warn(
                        "Terms version mismatch for user: expected={}, actual={}, uri={}",
                        currentVersion,
                        termsVersion,
                        request.getServletPath());
                writeForbiddenResponse(response);
                return;
            }
        } catch (Exception e) {
            log.warn("Failed to decode JWT for terms check: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();

        if (path.equals(termsProperties.termsAcceptPath())) {
            return true;
        }

        for (String prefix : SKIP_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }

        return publicApiEndpointRegistry.getPublicUrlSet().stream()
                .anyMatch(
                        pattern -> {
                            String base = pattern.replace("/**", "");
                            return path.equals(base) || path.startsWith(base + "/");
                        });
    }

    private void writeForbiddenResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String traceId = UUID.randomUUID().toString();
        ErrorResponse errorResponse = ErrorResponse.of(ErrorCode.AUTH_019).withTraceId(traceId);
        RestApiResponse<ErrorResponse> apiResponse =
                RestApiResponse.error(ErrorCode.AUTH_019.message(), errorResponse);

        objectMapper.writeValue(response.getOutputStream(), apiResponse);
    }
}
