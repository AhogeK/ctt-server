package com.ahogek.cttserver.audit.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditActionTest {

    @Test
    void description_returns_correct_value_for_authentication_actions() {
        assertThat(AuditAction.USER_REGISTERED.description()).isEqualTo("User registration");
        assertThat(AuditAction.LOGIN_SUCCESS.description()).isEqualTo("Successful login");
        assertThat(AuditAction.LOGIN_FAILED.description()).isEqualTo("Failed login attempt");
        assertThat(AuditAction.LOGOUT.description()).isEqualTo("User logout");
        assertThat(AuditAction.PASSWORD_CHANGED.description()).isEqualTo("Password updated");
        assertThat(AuditAction.PASSWORD_RESET_REQUESTED.description())
                .isEqualTo("Password reset requested");
        assertThat(AuditAction.EMAIL_VERIFIED.description()).isEqualTo("Email address verified");
        assertThat(AuditAction.ACCOUNT_LOCKED.description())
                .isEqualTo("Account locked due to violations");
    }

    @Test
    void description_returns_correct_value_for_credential_actions() {
        assertThat(AuditAction.API_KEY_CREATED.description()).isEqualTo("API Key generated");
        assertThat(AuditAction.API_KEY_REVOKED.description()).isEqualTo("API Key revoked");
        assertThat(AuditAction.DEVICE_REGISTERED.description()).isEqualTo("New device linked");
        assertThat(AuditAction.DEVICE_UNLINKED.description()).isEqualTo("Device unlinked");
    }

    @Test
    void description_returns_correct_value_for_domain_actions() {
        assertThat(AuditAction.SYNC_PUSH_COMPLETED.description()).isEqualTo("Sync push completed");
        assertThat(AuditAction.SYNC_CONFLICT_DETECTED.description())
                .isEqualTo("Sync conflict detected");
    }

    @Test
    void description_returns_correct_value_for_security_actions() {
        assertThat(AuditAction.SECURITY_VIOLATION.description())
                .isEqualTo("General security violation detected");
        assertThat(AuditAction.RATE_LIMIT_EXCEEDED.description())
                .isEqualTo("Rate limit quota exceeded");
        assertThat(AuditAction.UNAUTHORIZED_ACCESS.description())
                .isEqualTo("Unauthorized access attempt");
        assertThat(AuditAction.FORBIDDEN_ACCESS.description())
                .isEqualTo("Forbidden resource access attempt");
    }

    @Test
    void all_enum_values_have_non_blank_descriptions() {
        for (AuditAction action : AuditAction.values()) {
            assertThat(action.description()).isNotNull().isNotBlank().hasSizeGreaterThan(3);
        }
    }
}
