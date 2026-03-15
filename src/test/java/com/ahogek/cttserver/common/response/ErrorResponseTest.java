package com.ahogek.cttserver.common.response;

import com.ahogek.cttserver.common.exception.ErrorCode;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    @Test
    void of_withErrorCode_returnsErrorResponse() {
        ErrorResponse response = ErrorResponse.of(ErrorCode.COMMON_001);
        assertThat(response.code()).isEqualTo("COMMON_001");
        assertThat(response.message()).isEqualTo("Invalid request parameters");
        assertThat(response.httpStatus()).isEqualTo(400);
    }

    @Test
    void of_withErrorCodeAndCustomMessage_returnsErrorResponse() {
        ErrorResponse response = ErrorResponse.of(ErrorCode.COMMON_002, "Custom not found");
        assertThat(response.code()).isEqualTo("COMMON_002");
        assertThat(response.message()).isEqualTo("Custom not found");
    }

    @Test
    void of_withCodeAndMessage_returnsErrorResponse() {
        ErrorResponse response = ErrorResponse.of("TEST_CODE", "Test message");
        assertThat(response.code()).isEqualTo("TEST_CODE");
        assertThat(response.message()).isEqualTo("Test message");
    }

    @Test
    void of_withCodeMessageTraceId_returnsErrorResponse() {
        ErrorResponse response = ErrorResponse.of("TEST_CODE", "Test message", "trace-123");
        assertThat(response.code()).isEqualTo("TEST_CODE");
        assertThat(response.message()).isEqualTo("Test message");
        assertThat(response.traceId()).isEqualTo("trace-123");
    }

    @Test
    void withTraceId_addsTraceId() {
        ErrorResponse response = ErrorResponse.of(ErrorCode.COMMON_001).withTraceId("trace-456");
        assertThat(response.traceId()).isEqualTo("trace-456");
    }

    @Test
    void withHttpStatus_overridesHttpStatus() {
        ErrorResponse response = ErrorResponse.of(ErrorCode.COMMON_001).withHttpStatus(500);
        assertThat(response.httpStatus()).isEqualTo(500);
    }

    @Test
    void withDetails_addsDetails() {
        List<ErrorResponse.FieldError> details =
                List.of(
                        new ErrorResponse.FieldError("field1", "error1"),
                        new ErrorResponse.FieldError("field2", "error2"));
        ErrorResponse response = ErrorResponse.of(ErrorCode.COMMON_001).withDetails(details);
        assertThat(response.details()).hasSize(2);
    }

    @Test
    void addDetail_appendsToExistingDetails() {
        List<ErrorResponse.FieldError> initial =
                List.of(new ErrorResponse.FieldError("field1", "error1"));
        ErrorResponse response =
                ErrorResponse.of(ErrorCode.COMMON_001)
                        .withDetails(initial)
                        .addDetail("field2", "error2");
        assertThat(response.details()).hasSize(2);
    }
}
