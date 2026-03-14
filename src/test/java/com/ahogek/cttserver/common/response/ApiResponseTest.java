package com.ahogek.cttserver.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void ok_withoutData_returnsSuccess() {
        ApiResponse<Void> response = ApiResponse.ok();
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Operation successful");
        assertThat(response.data()).isNull();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void ok_withData_returnsSuccess() {
        ApiResponse<String> response = ApiResponse.ok("test data");
        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("test data");
    }

    @Test
    void ok_withDataAndMessage_returnsSuccess() {
        ApiResponse<Integer> response = ApiResponse.ok(42, "custom message");
        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo(42);
        assertThat(response.message()).isEqualTo("custom message");
    }

    @Test
    void error_returnsFailure() {
        ApiResponse<Void> response = ApiResponse.error("error message");
        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("error message");
    }

    @Test
    void error_withDetails_returnsFailure() {
        ApiResponse<List<String>> response =
                ApiResponse.error("validation failed", List.of("field1", "field2"));
        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("validation failed");
        assertThat(response.data()).containsExactly("field1", "field2");
    }
}
