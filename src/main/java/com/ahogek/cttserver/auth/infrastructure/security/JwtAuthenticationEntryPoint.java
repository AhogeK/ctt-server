package com.ahogek.cttserver.auth.infrastructure.security;

import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Custom JWT authentication entry point.
 *
 * <p>When a user attempts to access a protected resource without a valid JWT (e.g., token missing,
 * expired, or invalid signature), Spring Security triggers this entry point. It converts the
 * default 401 response into the system's standard RestApiResponse format with AUTH_003 error code.
 *
 * @author AhogeK
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);
    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        log.warn(
                "Authentication failed for {}: {}",
                request.getRequestURI(),
                authException.getMessage());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String traceId = UUID.randomUUID().toString();

        ErrorResponse errorResponse = ErrorResponse.of(ErrorCode.AUTH_003).withTraceId(traceId);

        RestApiResponse<ErrorResponse> apiResponse =
                RestApiResponse.error(ErrorCode.AUTH_003.message(), errorResponse);

        objectMapper.writeValue(response.getOutputStream(), apiResponse);
    }
}
