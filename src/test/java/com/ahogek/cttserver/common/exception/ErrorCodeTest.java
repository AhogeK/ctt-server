package com.ahogek.cttserver.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorCodeTest {

    @Test
    void commonCodes_haveCorrectHttpStatus() {
        assertThat(ErrorCode.COMMON_001.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.COMMON_002.httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.COMMON_003.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void authCodes_haveCorrectHttpStatus() {
        assertThat(ErrorCode.AUTH_001.httpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.AUTH_004.httpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void userCodes_haveCorrectHttpStatus() {
        assertThat(ErrorCode.USER_001.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.USER_004.httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void systemCodes_haveCorrectHttpStatus() {
        assertThat(ErrorCode.SYSTEM_001.httpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ErrorCode.SYSTEM_002.httpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void message_returnsDefaultMessage() {
        assertThat(ErrorCode.COMMON_001.message()).isEqualTo("Invalid request parameters");
    }

    @Test
    void message_withCustomMessage_returnsCustomMessage() {
        assertThat(ErrorCode.COMMON_001.message("custom")).isEqualTo("custom");
    }

    @Test
    void message_withNull_returnsDefaultMessage() {
        assertThat(ErrorCode.COMMON_001.message(null)).isEqualTo("Invalid request parameters");
    }
}
