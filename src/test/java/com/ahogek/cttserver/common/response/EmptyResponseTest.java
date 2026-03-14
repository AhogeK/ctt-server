package com.ahogek.cttserver.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
        assertThat(response.message()).isEqualTo("Custom message");
    }
}
