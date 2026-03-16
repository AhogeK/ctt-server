package com.ahogek.cttserver.audit.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditActionTest {

    @Test
    void description_returns_correct_value_for_iam_actions() {
        assertThat(AuditAction.REGISTER_REQUESTED.description())
                .isEqualTo("User registration requested");
        assertThat(AuditAction.REGISTER_SUCCESS.description())
                .isEqualTo("User registration completed");
        assertThat(AuditAction.LOGIN_SUCCESS.description()).isEqualTo("Successful login");
        assertThat(AuditAction.LOGIN_FAILED.description()).isEqualTo("Failed login attempt");
        assertThat(AuditAction.LOGOUT_SUCCESS.description())
                .isEqualTo("User logged out successfully");
        assertThat(AuditAction.ACCOUNT_LOCKED.description())
                .isEqualTo("Account automatically locked due to violations");
    }

    @Test
    void description_returns_correct_value_for_verification_actions() {
        assertThat(AuditAction.EMAIL_VERIFICATION_SENT.description())
                .isEqualTo("Verification email dispatched to outbox");
        assertThat(AuditAction.EMAIL_VERIFICATION_SUCCESS.description())
                .isEqualTo("Email address verified successfully");
        assertThat(AuditAction.EMAIL_VERIFICATION_FAILED.description())
                .isEqualTo("Email verification failed");
    }

    @Test
    void description_returns_correct_value_for_credential_actions() {
        assertThat(AuditAction.PASSWORD_RESET_REQUESTED.description())
                .isEqualTo("Password reset token generated and requested");
        assertThat(AuditAction.PASSWORD_RESET_SUCCESS.description())
                .isEqualTo("Password successfully reset via token");
        assertThat(AuditAction.PASSWORD_CHANGED.description())
                .isEqualTo("Password updated via user profile");
        assertThat(AuditAction.API_KEY_CREATED.description()).isEqualTo("New API key generated");
        assertThat(AuditAction.API_KEY_REVOKED.description()).isEqualTo("API key revoked");
    }

    @Test
    void description_returns_correct_value_for_device_actions() {
        assertThat(AuditAction.DEVICE_LINKED.description())
                .isEqualTo("New client device linked to account");
        assertThat(AuditAction.DEVICE_UNLINKED.description())
                .isEqualTo("Device forcefully unlinked");
    }

    @Test
    void description_returns_correct_value_for_security_actions() {
        assertThat(AuditAction.RATE_LIMIT_EXCEEDED.description())
                .isEqualTo("Rate limit quota exceeded");
        assertThat(AuditAction.UNAUTHORIZED_ACCESS.description())
                .isEqualTo("Unauthorized access attempt");
        assertThat(AuditAction.FORBIDDEN_ACCESS.description())
                .isEqualTo("Forbidden resource access attempt");
        assertThat(AuditAction.MALICIOUS_PAYLOAD_DETECTED.description())
                .isEqualTo("Potential malicious payload intercepted");
    }

    @Test
    void all_enum_values_have_non_blank_descriptions() {
        for (AuditAction action : AuditAction.values()) {
            assertThat(action.description()).isNotNull().isNotBlank().hasSizeGreaterThan(3);
        }
    }

    @Test
    void naming_convention_follows_bipartite_grammar() {
        for (AuditAction action : AuditAction.values()) {
            String name = action.name();
            assertThat(name).contains("_");

            String[] parts = name.split("_");
            assertThat(parts).hasSizeGreaterThanOrEqualTo(2);
        }
    }
}
