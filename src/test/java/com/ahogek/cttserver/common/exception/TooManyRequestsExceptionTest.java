package com.ahogek.cttserver.common.exception;

import com.ahogek.cttserver.common.response.ErrorResponse;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TooManyRequestsException}, focusing on the {@code retryAfter} field and
 * {@link TooManyRequestsException#toErrorResponse()} propagation.
 */
class TooManyRequestsExceptionTest {

    @Test
    void shouldCarryRetryAfter_whenConstructedWithTimestamp() {
        Instant retryAfter = Instant.now().plusSeconds(300);

        TooManyRequestsException ex =
                new TooManyRequestsException(ErrorCode.RATE_LIMIT_001, "msg", retryAfter);

        assertThat(ex.errorCode()).isEqualTo(ErrorCode.RATE_LIMIT_001);
        assertThat(ex.retryAfter()).isEqualTo(retryAfter);
    }

    @Test
    void shouldHaveNullRetryAfter_whenConstructedWithoutTimestamp() {
        TooManyRequestsException ex = new TooManyRequestsException(ErrorCode.MAIL_004);

        assertThat(ex.retryAfter()).isNull();
    }

    @Test
    void shouldPropagateRetryAfterInErrorResponse_whenPresent() {
        Instant retryAfter = Instant.now().plusSeconds(60);
        TooManyRequestsException ex =
                new TooManyRequestsException(ErrorCode.RATE_LIMIT_001, "msg", retryAfter);

        ErrorResponse response = ex.toErrorResponse();

        assertThat(response.code()).isEqualTo("RATE_LIMIT_001");
        assertThat(response.retryAfter()).isEqualTo(retryAfter);
    }

    @Test
    void shouldOmitRetryAfterInErrorResponse_whenAbsent() {
        TooManyRequestsException ex =
                new TooManyRequestsException(ErrorCode.MAIL_004, "custom", null);

        ErrorResponse response = ex.toErrorResponse();

        assertThat(response.code()).isEqualTo("MAIL_004");
        assertThat(response.retryAfter()).isNull();
    }
}
