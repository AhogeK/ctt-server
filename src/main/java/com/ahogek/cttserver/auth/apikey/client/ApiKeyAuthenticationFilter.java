package com.ahogek.cttserver.auth.apikey.client;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.auth.apikey.crypto.ApiKeyHasher;
import com.ahogek.cttserver.auth.apikey.entity.ApiKey;
import com.ahogek.cttserver.auth.apikey.enums.ApiKeyScope;
import com.ahogek.cttserver.auth.apikey.model.ApiKeyPrincipal;
import com.ahogek.cttserver.auth.apikey.service.ApiKeyService;
import com.ahogek.cttserver.common.config.properties.SecurityProperties;
import com.ahogek.cttserver.common.context.RequestContext;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.ForbiddenException;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.exception.UnauthorizedException;
import com.ahogek.cttserver.common.ratelimit.core.RedisRateLimiter;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Spring Security filter for API Key authentication.
 *
 * <p>Intercepts requests with {@code Authorization: Bearer cttak_*} headers and validates the API
 * key. If valid, sets the SecurityContext with an {@link ApiKeyPrincipal}. If invalid, delegates to
 * the standard authentication entry point.
 *
 * <p>This filter runs before {@code JwtAuthenticationFilter} in the chain. Requests without an API
 * key header (or with a JWT Bearer token) pass through to the next filter unchanged.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-07-10
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

    private static final String AUTH_FAILURE_KEY_PREFIX = "api_key_auth_fail:";

    private final ApiKeyService apiKeyService;
    private final AuditLogService auditLogService;
    private final SecurityProperties.ApiKeyProperties apiKeyProperties;
    private final ObjectMapper objectMapper;
    private final RedisRateLimiter redisRateLimiter;

    public ApiKeyAuthenticationFilter(
            ApiKeyService apiKeyService,
            AuditLogService auditLogService,
            SecurityProperties securityProperties,
            ObjectMapper objectMapper,
            RedisRateLimiter redisRateLimiter) {
        this.apiKeyService = apiKeyService;
        this.auditLogService = auditLogService;
        this.apiKeyProperties = securityProperties.apiKey();
        this.objectMapper = objectMapper;
        this.redisRateLimiter = redisRateLimiter;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(apiKeyProperties.headerName());

        if (authHeader == null || !authHeader.startsWith(apiKeyProperties.headerPrefix() + " ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawKey = authHeader.substring(apiKeyProperties.headerPrefix().length() + 1).trim();

        if (!rawKey.startsWith(ApiKeyHasher.KEY_PREFIX_MARKER)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            ApiKey apiKey = apiKeyService.validateAndTouch(rawKey);

            ApiKeyPrincipal principal =
                    ApiKeyPrincipal.from(apiKey.getUser(), apiKey.getId(), apiKey.getScopes());

            List<SimpleGrantedAuthority> authorities =
                    apiKey.getScopes().stream()
                            .map(ApiKeyScope::getAuthority)
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug(
                    "API key authenticated for user {} key {}",
                    principal.userId(),
                    principal.keyId());

            auditLogService.logSuccess(
                    principal.userId(),
                    AuditAction.API_KEY_USED,
                    ResourceType.API_KEY,
                    principal.keyId().toString());

            filterChain.doFilter(request, response);

        } catch (NotFoundException ex) {
            handleAuthFailure(request, response, ex.errorCode());
        } catch (UnauthorizedException ex) {
            handleAuthFailure(request, response, ex.errorCode());
        } catch (ForbiddenException ex) {
            handleAuthFailure(request, response, ex.errorCode());
        }
    }

    private void handleAuthFailure(
            HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        String clientIp = RequestContext.current().map(info -> info.clientIp()).orElse("unknown");

        String rateLimitKey = AUTH_FAILURE_KEY_PREFIX + clientIp;
        boolean allowed =
                redisRateLimiter.isAllowed(
                        rateLimitKey,
                        apiKeyProperties.authFailureRateLimit(),
                        apiKeyProperties.authFailureRateLimitWindowSeconds());

        if (!allowed) {
            log.warn("API key auth rate limit exceeded for IP: {}", clientIp);
            response.setStatus(429);
            response.setHeader(
                    HttpHeaders.RETRY_AFTER,
                    String.valueOf(apiKeyProperties.authFailureRateLimitWindowSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            ErrorResponse errorResponse = ErrorResponse.of(ErrorCode.RATE_LIMIT_001);
            RestApiResponse<ErrorResponse> apiResponse =
                    RestApiResponse.error(ErrorCode.RATE_LIMIT_001.message(), errorResponse);
            objectMapper.writeValue(response.getOutputStream(), apiResponse);
            return;
        }

        log.warn(
                "API key authentication failed for {} from {}: {}",
                request.getRequestURI(),
                clientIp,
                errorCode.message());

        String traceId =
                RequestContext.current()
                        .map(info -> info.traceId())
                        .filter(id -> id != null && !id.isBlank())
                        .orElse(UUID.randomUUID().toString());

        auditLogService.logFailure(
                null,
                AuditAction.API_KEY_AUTH_FAILED,
                ResourceType.API_KEY,
                null,
                errorCode.name());

        response.setStatus(errorCode.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ErrorResponse errorResponse = ErrorResponse.of(errorCode).withTraceId(traceId);
        RestApiResponse<ErrorResponse> apiResponse =
                RestApiResponse.error(errorCode.message(), errorResponse);

        objectMapper.writeValue(response.getOutputStream(), apiResponse);
    }
}
