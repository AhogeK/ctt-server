package com.ahogek.cttserver.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(ErrorCode.AUTH_002.httpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.AUTH_003.httpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.AUTH_004.httpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.AUTH_005.httpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.AUTH_006.httpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.AUTH_007.httpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.AUTH_008.httpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.AUTH_009.httpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.AUTH_010.httpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.AUTH_011.httpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.AUTH_012.httpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.AUTH_013.httpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.AUTH_015.httpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ErrorCode.AUTH_016.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.AUTH_017.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.AUTH_018.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.PASSWORD_SAME_AS_OLD.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void userCodes_haveCorrectHttpStatus() {
        assertThat(ErrorCode.USER_001.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.USER_004.httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.USER_007.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
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
    void message_withNull_returnsDefaultMessage() {
        assertThat(ErrorCode.COMMON_001.message(null)).isEqualTo("Invalid request parameters");
    }
}
