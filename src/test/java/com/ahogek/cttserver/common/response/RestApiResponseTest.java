package com.ahogek.cttserver.common.response;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RestApiResponseTest {

    @Test
    void ok_withoutData_returnsSuccess() {
        RestApiResponse<Void> response = RestApiResponse.ok();
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Operation successful");
        assertThat(response.data()).isNull();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void ok_withData_returnsSuccess() {
        RestApiResponse<String> response = RestApiResponse.ok("test data");
        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("test data");
    }

    @Test
    void ok_withDataAndMessage_returnsSuccess() {
        RestApiResponse<Integer> response = RestApiResponse.ok(42, "custom message");
        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo(42);
        assertThat(response.message()).isEqualTo("custom message");
    }

    @Test
    void error_returnsFailure() {
        RestApiResponse<Void> response = RestApiResponse.error("error message");
        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("error message");
    }

    @Test
    void error_withDetails_returnsFailure() {
        RestApiResponse<List<String>> response =
                RestApiResponse.error("validation failed", List.of("field1", "field2"));
        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("validation failed");
        assertThat(response.data()).containsExactly("field1", "field2");
    }
}
