package com.ahogek.cttserver.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ahogek.cttserver.common.response.ErrorResponse;

class BusinessExceptionTest {

    @Test
    void constructor_withErrorCode_setsErrorCode() {
        ValidationException ex = new ValidationException(ErrorCode.COMMON_003);
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.COMMON_003);
        assertThat(ex.getMessage()).isEqualTo("Validation error");
    }

    @Test
    void constructor_withErrorCodeAndMessage_setsCustomMessage() {
        ValidationException ex =
                new ValidationException(ErrorCode.COMMON_003, "custom validation error");
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.COMMON_003);
        assertThat(ex.getMessage()).isEqualTo("custom validation error");
    }

    @Test
    void toErrorResponse_withDefaultMessage() {
        NotFoundException ex = new NotFoundException(ErrorCode.COMMON_002);
        ErrorResponse response = ex.toErrorResponse();

        assertThat(response.code()).isEqualTo("COMMON_002");
        assertThat(response.message()).isEqualTo("Resource not found");
    }

    @Test
    void toErrorResponse_withCustomMessage() {
        NotFoundException ex = new NotFoundException(ErrorCode.COMMON_002, "User not found");
        ErrorResponse response = ex.toErrorResponse();

        assertThat(response.code()).isEqualTo("COMMON_002");
        assertThat(response.message()).isEqualTo("User not found");
    }

    @Test
    void notFoundException_canBeThrown() {
        assertThatThrownBy(
                        () -> {
                            throw new NotFoundException(ErrorCode.USER_004);
                        })
                .isInstanceOf(BusinessException.class)
                .hasMessage("User not found");
    }

    @Test
    void unauthorizedException_canBeThrown() {
        assertThatThrownBy(
                        () -> {
                            throw new UnauthorizedException(ErrorCode.AUTH_001);
                        })
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void forbiddenException_canBeThrown() {
        assertThatThrownBy(
                        () -> {
                            throw new ForbiddenException(ErrorCode.AUTH_004);
                        })
                .isInstanceOf(BusinessException.class)
                .hasMessage("Account locked");
    }

    @Test
    void conflictException_canBeThrown() {
        assertThatThrownBy(
                        () -> {
                            throw new ConflictException(ErrorCode.USER_001);
                        })
                .isInstanceOf(BusinessException.class)
                .hasMessage("Email already registered");
    }

    @Test
    void validationException_canBeThrown() {
        assertThatThrownBy(
                        () -> {
                            throw new ValidationException(ErrorCode.COMMON_003);
                        })
                .isInstanceOf(BusinessException.class)
                .hasMessage("Validation error");
    }

    @Test
    void tooManyRequestsException_canBeThrown() {
        assertThatThrownBy(
                        () -> {
                            throw new TooManyRequestsException(ErrorCode.RATE_LIMIT_001);
                        })
                .isInstanceOf(BusinessException.class)
                .hasMessage("Too many requests");
    }
}
