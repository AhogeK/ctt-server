package com.ahogek.cttserver.audit.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecuritySeverityTest {

    @Test
    void enum_values_exist() {
        assertThat(SecuritySeverity.values()).hasSize(3);
        assertThat(SecuritySeverity.values())
                .containsExactly(
                        SecuritySeverity.INFO, SecuritySeverity.WARNING, SecuritySeverity.CRITICAL);
    }

    @Test
    void value_of_returns_correct_enum() {
        assertThat(SecuritySeverity.valueOf("INFO")).isEqualTo(SecuritySeverity.INFO);
        assertThat(SecuritySeverity.valueOf("WARNING")).isEqualTo(SecuritySeverity.WARNING);
        assertThat(SecuritySeverity.valueOf("CRITICAL")).isEqualTo(SecuritySeverity.CRITICAL);
    }
}
