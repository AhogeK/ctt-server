package com.ahogek.cttserver.common.exception;

import com.ahogek.cttserver.common.response.ErrorResponse;

import jakarta.validation.ConstraintViolationException;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    @Test
    void shouldReturn400WithFieldErrors_whenMethodArgumentNotValid() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "name", "must not be blank");
        given(bindingResult.getFieldErrors()).willReturn(List.of(fieldError));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        given(ex.getBindingResult()).willReturn(bindingResult);

        ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mockPublisher);

        ResponseEntity<ErrorResponse> result = handler.handleValidationException(ex);

        assertThat(result.getStatusCode().value()).isEqualTo(400);
        assertThat(result.getBody())
                .isNotNull()
                .satisfies(
                        body -> {
                            assertThat(body.code()).isEqualTo("COMMON_003");
                            assertThat(body.traceId()).isNotNull();
                            assertThat(body.details()).hasSize(1);
                            assertThat(body.details().getFirst().field()).isEqualTo("name");
                        });
    }

    @Test
    void shouldReturn404_whenBusinessExceptionThrown() {
        NotFoundException ex = new NotFoundException(ErrorCode.COMMON_002, "Resource not found");
        ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mockPublisher);

        ResponseEntity<ErrorResponse> result = handler.handleBusinessException(ex);

        assertThat(result.getStatusCode().value()).isEqualTo(404);
        assertThat(result.getBody())
                .isNotNull()
                .satisfies(
                        body -> {
                            assertThat(body.code()).isEqualTo("COMMON_002");
                            assertThat(body.traceId()).isNotNull();
                        });
    }

    @Test
    void shouldReturn500WithoutStackTrace_whenInternalServerErrorException() {
        InternalServerErrorException ex = new InternalServerErrorException("Internal system error");
        ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mockPublisher);

        ResponseEntity<ErrorResponse> result = handler.handleInternalServerError(ex);

        assertThat(result.getStatusCode().value()).isEqualTo(500);
        assertThat(result.getBody())
                .isNotNull()
                .satisfies(
                        body -> {
                            assertThat(body.code()).isEqualTo("SYSTEM_001");
                            assertThat(body.traceId()).isNotNull();
                        });
    }

    @Test
    void shouldReturn400_whenMalformedJson() {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("JSON parse error", null);

        ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mockPublisher);

        ResponseEntity<ErrorResponse> result = handler.handleUnreadableMessage(ex);

        assertThat(result.getStatusCode().value()).isEqualTo(400);
        assertThat(result.getBody())
                .isNotNull()
                .satisfies(
                        body -> {
                            assertThat(body.code()).isEqualTo("COMMON_001");
                            assertThat(body.traceId()).isNotNull();
                        });
    }

    @Test
    void shouldReturn400_whenMissingServletRequestParameter() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("param", "String");

        ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mockPublisher);

        ResponseEntity<ErrorResponse> result = handler.handleMissingParam(ex);

        assertThat(result.getStatusCode().value()).isEqualTo(400);
        assertThat(result.getBody())
                .isNotNull()
                .satisfies(
                        body -> {
                            assertThat(body.code()).isEqualTo("COMMON_005");
                            assertThat(body.traceId()).isNotNull();
                        });
    }

    @Test
    void shouldReturn400_whenIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid value");

        ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mockPublisher);

        ResponseEntity<ErrorResponse> result = handler.handleIllegalArgumentException(ex);

        assertThat(result.getStatusCode().value()).isEqualTo(400);
        assertThat(result.getBody())
                .isNotNull()
                .satisfies(
                        body -> {
                            assertThat(body.code()).isEqualTo("COMMON_001");
                            assertThat(body.traceId()).isNotNull();
                        });
    }

    @Test
    void shouldReturn400WithFieldErrors_whenConstraintViolation() {
        ConstraintViolationException ex = new ConstraintViolationException(Collections.emptySet());
        ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mockPublisher);
        ResponseEntity<ErrorResponse> result = handler.handleConstraintViolation(ex);

        assertThat(result.getStatusCode().value()).isEqualTo(400);
        assertThat(result.getBody())
                .isNotNull()
                .satisfies(
                        body -> {
                            assertThat(body.code()).isEqualTo("COMMON_003");
                            assertThat(body.traceId()).isNotBlank();
                        });
    }

    @Test
    void shouldReturn401_whenUnauthorizedException() {
        UnauthorizedException ex = new UnauthorizedException(ErrorCode.AUTH_003, "Invalid token");
        ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mockPublisher);
        ResponseEntity<ErrorResponse> result = handler.handleSecurityException(ex);

        assertThat(result.getStatusCode().value()).isEqualTo(401);
        assertThat(result.getBody())
                .isNotNull()
                .satisfies(
                        body -> {
                            assertThat(body.code()).isEqualTo("AUTH_003");
                            assertThat(body.traceId()).isNotBlank();
                        });
    }

    @Test
    void shouldReturn403_whenForbiddenException() {
        ForbiddenException ex = new ForbiddenException(ErrorCode.AUTH_005, "Access denied");
        ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mockPublisher);
        ResponseEntity<ErrorResponse> result = handler.handleSecurityException(ex);

        assertThat(result.getStatusCode().value()).isEqualTo(403);
        assertThat(result.getBody())
                .isNotNull()
                .satisfies(
                        body -> {
                            assertThat(body.code()).isEqualTo("AUTH_005");
                            assertThat(body.traceId()).isNotBlank();
                        });
    }

    @Test
    void shouldReturn403WithRetryAfter_whenAccountLocked() {
        Instant retryAfter = Instant.now().plus(Duration.ofMinutes(30));
        AccountLockedException ex = new AccountLockedException(ErrorCode.AUTH_004, retryAfter);
        ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mockPublisher);
        ResponseEntity<ErrorResponse> result = handler.handleAccountLockedException(ex);

        assertThat(result.getStatusCode().value()).isEqualTo(403);
        assertThat(result.getBody())
                .isNotNull()
                .satisfies(
                        body -> {
                            assertThat(body.code()).isEqualTo("AUTH_004");
                            assertThat(body.retryAfter()).isEqualTo(retryAfter);
                        });
        assertThat(result.getHeaders().getFirst("Retry-After")).isNotNull();
    }

    @Test
    void shouldOmitRetryAfterHeader_whenRetryAfterIsNull() {
        AccountLockedException ex = new AccountLockedException(ErrorCode.AUTH_004, null);
        ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mockPublisher);
        ResponseEntity<ErrorResponse> result = handler.handleAccountLockedException(ex);

        assertThat(result.getStatusCode().value()).isEqualTo(403);
        assertThat(result.getBody())
                .isNotNull()
                .satisfies(
                        body -> {
                            assertThat(body.code()).isEqualTo("AUTH_004");
                            assertThat(body.retryAfter()).isNull();
                        });
        assertThat(result.getHeaders().getFirst("Retry-After")).isNull();
    }

    @Test
    void shouldReturn409_whenDataIntegrityViolation() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Duplicate key");
        ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(mockPublisher);
        ResponseEntity<ErrorResponse> result = handler.handleDataIntegrityViolation(ex);

        assertThat(result.getStatusCode().value()).isEqualTo(409);
        assertThat(result.getBody())
                .isNotNull()
                .satisfies(
                        body -> {
                            assertThat(body.code()).isEqualTo("USER_001");
                            assertThat(body.traceId()).isNotBlank();
                        });
    }
}
