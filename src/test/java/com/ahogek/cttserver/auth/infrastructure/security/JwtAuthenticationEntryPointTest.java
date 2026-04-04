package com.ahogek.cttserver.auth.infrastructure.security;

import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.response.ErrorResponse;
import com.ahogek.cttserver.common.response.RestApiResponse;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for JwtAuthenticationEntryPoint.
 *
 * <p>Tests the authentication entry point behavior when handling authentication failures. This test
 * follows TDD RED phase - the implementation class does not exist yet.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-04-02
 */
@DisplayName("JwtAuthenticationEntryPoint Tests")
class JwtAuthenticationEntryPointTest {

    private JwtAuthenticationEntryPoint entryPoint;
    private ObjectMapper objectMapper;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() throws IOException {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        entryPoint = new JwtAuthenticationEntryPoint(objectMapper);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        outputStream = new ByteArrayOutputStream();

        when(response.getOutputStream()).thenReturn(new MockServletOutputStream(outputStream));
        when(request.getRequestURI()).thenReturn("/api/protected");
    }

    @Test
    @DisplayName("Should return 401 with AUTH_003 when InsufficientAuthenticationException")
    void shouldReturn401WithAuth003Code_whenInsufficientAuthenticationException() throws Exception {
        // Given
        AuthenticationException exception =
                new InsufficientAuthenticationException("No token provided");

        // When
        entryPoint.commence(request, response, exception);

        // Then
        verify(response).setStatus(401);
        verify(response).setContentType("application/json");

        String jsonResponse = outputStream.toString();
        RestApiResponse<ErrorResponse> apiResponse =
                objectMapper.readValue(
                        jsonResponse,
                        objectMapper
                                .getTypeFactory()
                                .constructParametricType(
                                        RestApiResponse.class, ErrorResponse.class));

        assertThat(apiResponse.success()).isFalse();
        assertThat(apiResponse.data().code()).isEqualTo(ErrorCode.AUTH_003.name());
        assertThat(apiResponse.data().httpStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Should handle expired token exception")
    void shouldHandleExpiredTokenException_whenTokenExpired() throws Exception {
        // Given
        AuthenticationException exception = new BadCredentialsException("Token expired");

        // When
        entryPoint.commence(request, response, exception);

        // Then
        verify(response).setStatus(401);
        String jsonResponse = outputStream.toString();
        assertThat(jsonResponse).contains("AUTH_003");
    }

    @Test
    @DisplayName("Should handle invalid credentials")
    void shouldHandleInvalidCredentials_whenInvalidToken() throws Exception {
        // Given
        AuthenticationException exception = new BadCredentialsException("Invalid token");

        // When
        entryPoint.commence(request, response, exception);

        // Then
        verify(response).setStatus(401);
        String jsonResponse = outputStream.toString();
        assertThat(jsonResponse).contains("AUTH_003");
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
