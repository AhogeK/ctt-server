package com.ahogek.cttserver.common.response;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmptyResponseTest {

    @Test
    void ok_withoutMessage_returnsSuccess() {
        EmptyResponse response = EmptyResponse.ok();
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Operation successful");
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void ok_withMessage_returnsSuccess() {
        EmptyResponse response = EmptyResponse.ok("Custom message");
        assertThat(response.success()).isTrue();
        assertThat(response.idempotentSkip()).isNull();
        assertThat(response.message()).isEqualTo("Custom message");
    }

    @Test
    void shouldReturnNullIdempotentSkip_whenOkWithoutMessage() {
        EmptyResponse response = EmptyResponse.ok();
        assertThat(response.idempotentSkip()).isNull();
    }

    @Test
    void shouldReturnNullIdempotentSkip_whenOkWithMessage() {
        EmptyResponse response = EmptyResponse.ok("Custom message");
        assertThat(response.idempotentSkip()).isNull();
    }

    @Test
    void shouldReturnTrueIdempotentSkip_whenOkWithTrueFlag() {
        EmptyResponse response = EmptyResponse.ok(true);
        assertThat(response.success()).isTrue();
        assertThat(response.idempotentSkip()).isTrue();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void shouldReturnFalseIdempotentSkip_whenOkWithMessageAndFalseFlag() {
        EmptyResponse response = EmptyResponse.ok("Skipped due to idempotent window", false);
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Skipped due to idempotent window");
        assertThat(response.idempotentSkip()).isFalse();
    }

    @Test
    void shouldReturnTrueIdempotentSkip_whenOkWithMessageAndTrueFlag() {
        EmptyResponse response = EmptyResponse.ok("Request processed", true);
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Request processed");
        assertThat(response.idempotentSkip()).isTrue();
    }
}
