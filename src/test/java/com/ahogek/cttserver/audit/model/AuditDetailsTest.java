package com.ahogek.cttserver.audit.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditDetailsTest {

    @Test
    void empty_creates_all_null_fields() {
        AuditDetails details = AuditDetails.empty();

        assertThat(details.reason()).isNull();
        assertThat(details.errorCode()).isNull();
        assertThat(details.attemptCount()).isNull();
        assertThat(details.stateBefore()).isNull();
        assertThat(details.stateAfter()).isNull();
        assertThat(details.ext()).isNull();
    }

    @Test
    void reason_sets_reason_field_only() {
        AuditDetails details = AuditDetails.reason("Invalid password");

        assertThat(details.reason()).isEqualTo("Invalid password");
        assertThat(details.errorCode()).isNull();
        assertThat(details.attemptCount()).isNull();
    }

    @Test
    void error_sets_error_code_and_reason() {
        AuditDetails details = AuditDetails.error("AUTH_001", "Token expired");

        assertThat(details.errorCode()).isEqualTo("AUTH_001");
        assertThat(details.reason()).isEqualTo("Token expired");
        assertThat(details.attemptCount()).isNull();
    }

    @Test
    void attempt_sets_count_and_reason() {
        AuditDetails details = AuditDetails.attempt(3, "Retry limit reached");

        assertThat(details.attemptCount()).isEqualTo(3);
        assertThat(details.reason()).isEqualTo("Retry limit reached");
        assertThat(details.errorCode()).isNull();
    }

    @Test
    void transition_sets_before_and_after_states() {
        AuditDetails details =
                AuditDetails.transition("{\"status\": \"active\"}", "{\"status\": \"locked\"}");

        assertThat(details.stateBefore()).isEqualTo("{\"status\": \"active\"}");
        assertThat(details.stateAfter()).isEqualTo("{\"status\": \"locked\"}");
        assertThat(details.reason()).isNull();
    }

    @Test
    void extension_sets_ext_map() {
        Map<String, Object> ext = Map.of("customField", "customValue", "count", 42);
        AuditDetails details = AuditDetails.extension(ext);

        assertThat(details.ext()).containsEntry("customField", "customValue");
        assertThat(details.ext()).containsEntry("count", 42);
        assertThat(details.reason()).isNull();
    }

    @Test
    void factory_methods_create_immutable_instances() {
        AuditDetails details1 = AuditDetails.reason("test");
        AuditDetails details2 = AuditDetails.reason("test");

        assertThat(details1).isEqualTo(details2).isNotSameAs(details2);
    }
}
