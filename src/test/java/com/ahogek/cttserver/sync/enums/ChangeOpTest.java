package com.ahogek.cttserver.sync.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChangeOp Enum Tests")
class ChangeOpTest {

    @Test
    @DisplayName("shouldExposeExactlyTheValuesAllowedByTheDdlCheckConstraint")
    void shouldExposeExactlyTheValuesAllowedByTheDdlCheckConstraint() {
        assertThat(ChangeOp.values()).containsExactly(ChangeOp.UPSERT, ChangeOp.DELETE);
    }

    @Test
    @DisplayName("shouldPersistAsStringsMatchingTheDdlCheckConstraint")
    void shouldPersistAsStringsMatchingTheDdlCheckConstraint() {
        assertThat(ChangeOp.UPSERT.name()).isEqualTo("UPSERT");
        assertThat(ChangeOp.DELETE.name()).isEqualTo("DELETE");
    }
}
