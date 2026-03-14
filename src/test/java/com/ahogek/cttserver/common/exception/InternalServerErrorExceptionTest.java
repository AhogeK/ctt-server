package com.ahogek.cttserver.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalServerErrorExceptionTest {

    @Test
    void constructor_withMessage_setsMessage() {
        InternalServerErrorException ex = new InternalServerErrorException("internal error");
        assertThat(ex.getMessage()).isEqualTo("internal error");
    }

    @Test
    void constructor_withMessageAndCause_setsBoth() {
        Throwable cause = new RuntimeException("root cause");
        InternalServerErrorException ex = new InternalServerErrorException("internal error", cause);
        assertThat(ex.getMessage()).isEqualTo("internal error");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    void canBeThrown() {
        assertThatThrownBy(
                        () -> {
                            throw new InternalServerErrorException("test error");
                        })
                .isInstanceOf(RuntimeException.class)
                .hasMessage("test error");
    }
}
